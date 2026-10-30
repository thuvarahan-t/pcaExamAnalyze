package com.example.pcaExamAnalyze;

import com.example.pcaExamAnalyze.domain.*;
import com.example.pcaExamAnalyze.repo.*;
import com.example.pcaExamAnalyze.service.*;
import com.example.pcaExamAnalyze.web.dto.McqExamForm;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.BeanPropertyBindingResult;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real H2 + Spring transactions for admin exam lifecycle.
 * Release, rescore, teacher scheduling and batch rules should not regress when saving is optimized.
 */
class AdminExamLifecycleTest {
    private static ConfigurableApplicationContext ctx;
    private static TransactionTemplate tx;
    private static McqAdminService admin;
    private static McqExamRepository exams;
    private static McqSubmissionRepository submissions;

    @BeforeAll static void boot() {
        ctx = new SpringApplicationBuilder(PcaExamAnalyzeApplication.class).run(
                "--spring.profiles.active=dev",
                "--spring.datasource.url=jdbc:h2:mem:adminlifecycle;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "--server.port=0", "--pca.teacher.username=coverage-teacher",
                "--pca.teacher.password=coverage-test-password",
                "--logging.level.root=ERROR");
        tx = new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class));
        admin = ctx.getBean(McqAdminService.class);
        exams = ctx.getBean(McqExamRepository.class);
        submissions = ctx.getBean(McqSubmissionRepository.class);
    }
    @AfterAll static void close() { if (ctx != null) ctx.close(); }

    private static McqExamForm form(String name, int total) {
        var f = new McqExamForm();
        f.setName(name);
        f.setBatch("2027 A/L");
        f.setTotalQuestions(total);
        f.setExamYear(2027);
        f.setExamMonth("October");
        f.setPaperDriveUrl("https://drive.google.com/file/d/testpaper/view");
        f.setOpenAt(LocalDateTime.now(McqAdminService.PCA_ZONE).minusHours(1));
        f.setDurationMinutes(60);
        f.setAnswers(new LinkedHashMap<>());
        for (int q = 1; q <= total; q++) f.getAnswers().put(q, List.of(1));
        return f;
    }
    private static McqExam saveExam(String label, int total, boolean publish) {
        return admin.save(null, form("Coverage " + label + " " + UUID.randomUUID(), total), publish, "coverage-teacher").exam();
    }
    private static Long student(long examId, Map<Integer,Integer> answers, McqSubmissionStatus state) {
        return tx.execute(status -> {
            var s = new McqSubmission();
            s.setExam(exams.getReferenceById(examId));
            s.setReceiptNumber("LIFE-" + UUID.randomUUID());
            s.setStudentName("Lifecycle Student");
            s.setRegistrationId("LIFE" + UUID.randomUUID().toString().substring(0, 8));
            s.setNic("200000000001");
            s.setEmail("student@example.test");
            s.setBatch("2027 A/L");
            s.setSchool("Test School");
            s.setStream("Physical Science");
            s.setDistrict("Jaffna");
            s.setStatus(state);
            s.setStartedAt(Instant.now().minusSeconds(100));
            s.setSubmittedAt(state == McqSubmissionStatus.SUBMITTED ? Instant.now() : null);
            s.getAnswers().putAll(answers);
            return submissions.save(s).getId();
        });
    }

    @Test void activeBatchesCanBeAddedRenamedDeactivatedAndDeleted() {
        String name = "Coverage " + UUID.randomUUID().toString().substring(0, 7);
        String renamed = name + " Renamed";
        admin.addBatch("  " + name + "  ");
        assertTrue(admin.activeBatchNames().contains(name));
        var b = admin.batchRows().stream().filter(row -> row.name().equals(name)).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> admin.addBatch(name.toUpperCase(Locale.ROOT)));
        admin.renameBatch(b.id(), " " + renamed + " ");
        assertFalse(admin.activeBatchNames().contains(name));
        assertTrue(admin.activeBatchNames().contains(renamed));
        admin.toggleBatch(b.id());
        assertFalse(admin.activeBatchNames().contains(renamed));
        admin.toggleBatch(b.id());
        assertTrue(admin.activeBatchNames().contains(renamed));
        admin.deleteBatch(b.id());
        assertFalse(admin.activeBatchNames().contains(renamed));
    }

    @Test void batchMutationsRejectBadInputAndMissingRows() {
        assertThrows(IllegalArgumentException.class, () -> admin.addBatch("  "));
        assertThrows(IllegalArgumentException.class, () -> admin.addBatch("x".repeat(81)));
        assertThrows(IllegalArgumentException.class, () -> admin.renameBatch(-1L, "Unknown"));
        assertThrows(IllegalArgumentException.class, () -> admin.toggleBatch(-1L));
        assertThrows(IllegalArgumentException.class, () -> admin.deleteBatch(-1L));
    }

    @Test void formValidationNormalizesOptionsAndRejectsWrongBatchOrPaperLink() {
        var f = form("Validation", 3);
        f.setBatch("unknown-batch");
        f.setExamMonth("Invalid Month");
        f.setPaperDriveUrl("");
        f.setAnswers(new LinkedHashMap<>());
        f.getAnswers().put(1, Arrays.asList(null, 1, 1, 8, -1, 2));
        f.getAnswers().put(4, List.of(1));
        var binding = new BeanPropertyBindingResult(f, "exam");
        admin.validateForm(f, binding);
        assertNotNull(binding.getFieldError("batch"));
        assertNotNull(binding.getFieldError("examMonth"));
        assertNotNull(binding.getFieldError("paperDriveUrl"));
        assertEquals(List.of(1, 2), f.getAnswers().get(1));
        assertFalse(f.getAnswers().containsKey(4));
        assertEquals(1, f.getAnswers().size());
    }

    @Test void publishedExamCreationMakesPublicExamVisibleAndCanBeClosedAndOpened() {
        var exam = saveExam("published", 4, true);
        assertEquals(McqExamPublicationState.PUBLISHED, exam.getPublicationState());
        assertTrue(admin.publicExamRows().stream().anyMatch(e -> e.id().equals(exam.getId())));
        assertEquals(4, admin.formFor(exam.getId()).getAnswers().size());
        assertEquals(4, admin.optionNumbers().size() + 1);
        assertTrue(admin.questionNumbers().size() >= 4);
        admin.setExamClosed(exam.getId(), true);
        assertTrue(admin.requireExam(exam.getId()).isManualClose());
        admin.setExamClosed(exam.getId(), false);
        assertFalse(admin.requireExam(exam.getId()).isManualClose());
        admin.archive(exam.getId());
        assertEquals(McqExamPublicationState.ARCHIVED, admin.requireExam(exam.getId()).getPublicationState());
        assertThrows(IllegalStateException.class, () -> admin.setExamClosed(exam.getId(), true));
    }

    @Test void incompleteAnswerKeyBlocksReleaseAndCompleteKeyRescoresSubmittedPapers() {
        var exam = saveExam("release", 4, true);
        Long id = student(exam.getId(), Map.of(1, 1, 2, 2, 3, 1), McqSubmissionStatus.SUBMITTED);
        tx.executeWithoutResult(status -> {
            var e = exams.findById(exam.getId()).orElseThrow();
            e.setCorrectOptionsFor(4, List.of());
        });
        IllegalStateException rejection = assertThrows(IllegalStateException.class, () -> admin.releaseResults(exam.getId()));
        assertTrue(rejection.getMessage().contains("Q4"));
        tx.executeWithoutResult(status -> {
            exams.findById(exam.getId()).orElseThrow().setCorrectOptionsFor(4, List.of(1));
        });
        admin.releaseResults(exam.getId());
        tx.executeWithoutResult(status -> {
            var report = submissions.findStudentSessionById(id).orElseThrow();
            assertEquals(2, report.getScore());
            assertEquals(50.0, report.getPercentage());
            assertEquals(1, report.getUnansweredCount());
            assertNotNull(report.getResultCalculatedAt());
            assertTrue(report.getExam().isResultsPublished());
        });
        admin.unpublishResults(exam.getId());
        assertFalse(admin.requireExam(exam.getId()).isResultsPublished());
    }

    @Test void draftExamsDoNotAllowManualCloseAndKeepPublicResultsHidden() {
        var draft = saveExam("draft", 2, false);
        assertEquals(McqExamPublicationState.DRAFT, draft.getPublicationState());
        assertThrows(IllegalStateException.class, () -> admin.setExamClosed(draft.getId(), true));
        assertFalse(admin.publicExamRows().stream().anyMatch(r -> r.id().equals(draft.getId())));
        var duplicate = admin.duplicate(draft.getId(), "coverage-teacher");
        assertEquals(McqExamPublicationState.DRAFT, duplicate.getPublicationState());
        assertEquals(draft.getAnswerKey(), duplicate.getAnswerKey());
        assertTrue(duplicate.getSlug().startsWith("copy-of-"));
        assertFalse(duplicate.isResultsPublished());
    }

    @Test void teacherExportEscapesCsvAndSubmissionDeletionRejectsUnknownPaper() {
        var exam = saveExam("csv", 2, true);
        Long id = student(exam.getId(), Map.of(1, 1, 2, 2), McqSubmissionStatus.SUBMITTED);
        byte[] csv = admin.exportCsv(exam.getId());
        String text = new String(csv, StandardCharsets.UTF_8);
        assertTrue(text.contains("Lifecycle Student"));
        assertTrue(text.contains("receipt") || text.contains("Receipt"));
        assertFalse(admin.submissionRows(exam.getId(), "Lifecycle").isEmpty());
        admin.deleteSubmission(id);
        assertFalse(submissions.existsById(id));
        assertThrows(IllegalArgumentException.class, () -> admin.deleteSubmission(-1L));
    }
}
