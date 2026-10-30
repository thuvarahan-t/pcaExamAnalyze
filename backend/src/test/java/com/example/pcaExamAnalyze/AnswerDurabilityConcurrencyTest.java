package com.example.pcaExamAnalyze;

import com.example.pcaExamAnalyze.domain.*;
import com.example.pcaExamAnalyze.repo.*;
import com.example.pcaExamAnalyze.service.*;
import com.example.pcaExamAnalyze.web.dto.ExamStudentDetailsForm;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end regression tests for SQL autosave vs. ORM finalization.
 * Uses a real database and transaction manager, not mocks.
 * The production PostgreSQL path is covered by the companion Postgres test.
 */
class AnswerDurabilityConcurrencyTest {
    private static final String NIC = "200000000001";
    private static ConfigurableApplicationContext ctx;
    private static TransactionTemplate tx;
    private static McqExam exam;
    private static McqSubmissionRepository submissions;
    private static McqAnswerWriter writer;
    private static McqStudentExamService service;

    @BeforeAll static void start() {
        ctx = new SpringApplicationBuilder(PcaExamAnalyzeApplication.class).run(
                "--spring.profiles.active=dev", "--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:answerdurability;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "--spring.datasource.hikari.maximum-pool-size=5",
                "--logging.level.root=ERROR");
        tx = new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class));
        submissions = ctx.getBean(McqSubmissionRepository.class);
        writer = ctx.getBean(McqAnswerWriter.class);
        service = ctx.getBean(McqStudentExamService.class);
        exam = tx.execute(status -> {
            var e = new McqExam();
            e.setName("50 Question Durability Exam");
            e.setSlug("durability-50");
            e.setExamMonth("October");
            e.setExamYear(2026);
            e.setPaperDriveUrl("");
            e.setOpenAt(Instant.now().minusSeconds(600));
            e.setCloseAt(Instant.now().plusSeconds(3600));
            e.setResultReleaseAt(Instant.now().plusSeconds(3600));
            e.setPublicationState(McqExamPublicationState.PUBLISHED);
            e.setTotalQuestions(50);
            for (int q = 1; q <= 50; q++) e.getAnswerKey().put(q, 1);
            return ctx.getBean(McqExamRepository.class).save(e);
        });
    }

    @AfterAll static void stop() { if (ctx != null) ctx.close(); }

    private static long create(String registration) {
        return tx.execute(status -> {
            var s = new McqSubmission();
            s.setExam(exam);
            s.setReceiptNumber("DUR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
            s.setRegistrationId(registration);
            s.setNic(NIC);
            s.setStudentName("Durability Student");
            s.setEmail("student@example.test");
            s.setBatch("2027 A/L");
            s.setSchool("Test School");
            s.setStream("Physical Science");
            s.setDistrict("Jaffna");
            s.setStartedAt(Instant.now().minusSeconds(30));
            s.setStatus(McqSubmissionStatus.IN_PROGRESS);
            return submissions.save(s).getId();
        });
    }

    private static ExamStudentDetailsForm details(String registration) {
        var f = new ExamStudentDetailsForm();
        f.setRegistrationId(registration);
        f.setNic(NIC);
        return f;
    }

    private static Map<Integer, Integer> allAnswers() {
        var rows = new LinkedHashMap<Integer, Integer>();
        for (int q = 1; q <= 50; q++) rows.put(q, 1);
        return rows;
    }

    private static void assertComplete(long id) {
        tx.executeWithoutResult(status -> {
            var submission = submissions.findStudentSessionById(id).orElseThrow();
            assertEquals(McqSubmissionStatus.SUBMITTED, submission.getStatus());
            assertEquals(50, submission.getAnswers().size());
            assertEquals(allAnswers(), submission.getAnswers());
            assertEquals(50, submission.getScore());
            assertEquals(0, submission.getUnansweredCount());
        });
    }

    @Test void fiftyAutosavesThenSubmitNeverLosesAnswers() {
        String reg = "DUR-A";
        long id = create(reg);
        for (int q = 1; q <= 50; q++) assertTrue(writer.save(id, q, 1), "answer " + q);
        var result = service.finish(id, details(reg), allAnswers(), Map.of(), 50);
        assertEquals(50, result.savedAnswers());
        assertComplete(id);
    }

    @Test void repeatedSubmitIsIdempotentAndLateAutosaveCannotMutateResult() {
        String reg = "DUR-B";
        long id = create(reg);
        assertEquals(50, service.finish(id, details(reg), allAnswers(), Map.of(), 50).savedAnswers());
        assertEquals(50, service.finish(id, details(reg), allAnswers(), Map.of(), 50).savedAnswers());
        assertFalse(writer.save(id, 1, 2), "do not accept write after submit");
        assertFalse(writer.clear(id, 1), "do not clear after submit");
        assertComplete(id);
    }

    @Test void competingAutosaveWaitsForFinalSubmitAndCannotWriteAfterCommit() throws Exception {
        String reg = "DUR-C";
        long id = create(reg);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> finishing = pool.submit(() -> tx.executeWithoutResult(status -> {
                submissions.findByIdForUpdate(id).orElseThrow();
                locked.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                service.finish(id, details(reg), allAnswers(), Map.of(), 50);
            }));
            assertTrue(locked.await(5, TimeUnit.SECONDS));
            var attempted = new CountDownLatch(1);
            Future<Boolean> autosave = pool.submit(() -> {
                attempted.countDown();
                return writer.save(id, 1, 2);
            });
            assertTrue(attempted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> autosave.get(150, TimeUnit.MILLISECONDS),
                    "autosave must await the parent row lock");
            release.countDown();
            finishing.get(10, TimeUnit.SECONDS);
            assertFalse(autosave.get(10, TimeUnit.SECONDS));
            assertComplete(id);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test void independentStudentSubmissionsDoNotBlockEachOther() throws Exception {
        int count = 8;
        var pool = Executors.newFixedThreadPool(count);
        try {
            List<Future<Long>> tasks = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                final String reg = "DUR-M" + i;
                final long id = create(reg);
                tasks.add(pool.submit(() -> {
                    for (int q = 1; q <= 50; q++)
                        if (!writer.save(id, q, 1)) throw new AssertionError("lost autosave " + reg + ":" + q);
                    if (service.finish(id, details(reg), allAnswers(), Map.of(), 50).savedAnswers() != 50)
                        throw new AssertionError("final answer count mismatch for " + reg);
                    return id;
                }));
            }
            for (Future<Long> task : tasks) assertComplete(task.get(45, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }

    @Test void rejectedInvalidAnswerAndUnknownStudentDoNotCorruptPaper() {
        long id = create("DUR-INVALID");
        assertFalse(writer.save(id, 0, 1));
        assertFalse(writer.save(id, 51, 1));
        assertFalse(writer.save(id, 1, 9));
        assertFalse(writer.save(-1, 1, 1));
        assertFalse(writer.clear(id, 51));
        assertTrue(writer.save(id, 1, 1));
        assertEquals(50, service.finish(id, details("DUR-INVALID"), allAnswers(), Map.of(), 50).savedAnswers());
        assertComplete(id);
    }
}
