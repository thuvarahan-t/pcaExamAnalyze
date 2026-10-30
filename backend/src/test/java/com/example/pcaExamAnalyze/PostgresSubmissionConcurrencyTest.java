package com.example.pcaExamAnalyze;

import com.example.pcaExamAnalyze.domain.*;
import com.example.pcaExamAnalyze.repo.*;
import com.example.pcaExamAnalyze.service.*;
import com.example.pcaExamAnalyze.web.dto.ExamStudentDetailsForm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Runs against a REAL PostgreSQL instance when PCA_TEST_POSTGRES_URL is set. */
class PostgresSubmissionConcurrencyTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "PCA_TEST_POSTGRES_URL", matches = ".+")
    void upsertClearFinishAndConcurrentRequestPreserveAllFiftyAnswers() throws Exception {
        String jdbcUrl = System.getenv("PCA_TEST_POSTGRES_URL");
        String username = System.getenv().getOrDefault("PCA_TEST_POSTGRES_USER", "pca");
        String password = System.getenv().getOrDefault("PCA_TEST_POSTGRES_PASSWORD", "pca");
        try (var ctx = new SpringApplicationBuilder(PcaExamAnalyzeApplication.class).run(
                "--server.port=0", "--spring.datasource.url=" + jdbcUrl,
                "--spring.datasource.username=" + username, "--spring.datasource.password=" + password,
                "--spring.datasource.hikari.maximum-pool-size=4", "--logging.level.root=ERROR")) {
            var tx = new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class));
            var exams = ctx.getBean(McqExamRepository.class);
            var submissions = ctx.getBean(McqSubmissionRepository.class);
            var writer = ctx.getBean(McqAnswerWriter.class);
            var student = ctx.getBean(McqStudentExamService.class);
            var exam = tx.execute(status -> {
                var e = new McqExam();
                e.setName("PostgreSQL 50 MCQ Concurrency Test");
                e.setSlug("pg-race-" + UUID.randomUUID());
                e.setExamMonth("October");
                e.setExamYear(2026);
                e.setPaperDriveUrl("");
                e.setOpenAt(Instant.now().minusSeconds(600));
                e.setCloseAt(Instant.now().plusSeconds(6000));
                e.setResultReleaseAt(Instant.now().plusSeconds(6000));
                e.setTotalQuestions(50);
                e.setPublicationState(McqExamPublicationState.PUBLISHED);
                for (int q = 1; q <= 50; q++) e.getAnswerKey().put(q, 1);
                return exams.save(e);
            });
            long id = tx.execute(status -> {
                var s = new McqSubmission();
                s.setExam(exam);
                s.setReceiptNumber("PG-" + UUID.randomUUID());
                s.setStudentName("Postgres Student");
                s.setRegistrationId("PGDUR");
                s.setNic("200000000001");
                s.setEmail("student@example.test");
                s.setBatch("2027 A/L");
                s.setSchool("Test School");
                s.setStream("Physical Science");
                s.setDistrict("Jaffna");
                s.setStatus(McqSubmissionStatus.IN_PROGRESS);
                s.setStartedAt(Instant.now().minusSeconds(30));
                return submissions.save(s).getId();
            });
            var details = new ExamStudentDetailsForm();
            details.setRegistrationId("PGDUR");
            details.setNic("200000000001");
            var fifty = new LinkedHashMap<Integer, Integer>();
            for (int q = 1; q <= 50; q++) {
                fifty.put(q, 1);
                assertTrue(writer.save(id, q, 1));
            }
            assertTrue(writer.clear(id, 1));
            assertTrue(writer.save(id, 1, 1));
            writer.saveTimes(id, Map.of(1, 12, 2, 8));
            writer.saveTimes(id, Map.of(1, 5));
            var held = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                var finalizer = pool.submit(() -> tx.executeWithoutResult(status -> {
                    submissions.findByIdForUpdate(id).orElseThrow();
                    held.countDown();
                    try { assertTrue(release.await(8, TimeUnit.SECONDS)); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new RuntimeException(ex); }
                    assertEquals(50, student.finish(id, details, fifty, Map.of(), 50).savedAnswers());
                }));
                assertTrue(held.await(5, TimeUnit.SECONDS));
                var secondStarted = new CountDownLatch(1);
                Future<Boolean> lateWriter = pool.submit(() -> {
                    secondStarted.countDown();
                    return writer.save(id, 50, 2);
                });
                assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> lateWriter.get(150, TimeUnit.MILLISECONDS));
                release.countDown();
                finalizer.get(15, TimeUnit.SECONDS);
                assertFalse(lateWriter.get(15, TimeUnit.SECONDS));
                assertEquals(50, student.finish(id, details, fifty, Map.of(), 50).savedAnswers());
            } finally { release.countDown(); pool.shutdownNow(); }
            tx.executeWithoutResult(status -> {
                var s = submissions.findStudentSessionById(id).orElseThrow();
                assertEquals(McqSubmissionStatus.SUBMITTED, s.getStatus());
                assertEquals(fifty, s.getAnswers());
                assertEquals(50, s.getScore());
                assertEquals(12, s.getQuestionTimes().get(1));
            });
        }
    }
}
