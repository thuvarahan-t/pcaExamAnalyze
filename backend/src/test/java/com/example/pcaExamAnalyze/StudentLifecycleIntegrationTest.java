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

import static org.junit.jupiter.api.Assertions.*;

/** Integration coverage of the student journey, without changing the production exam UX. */
class StudentLifecycleIntegrationTest {
    private static ConfigurableApplicationContext ctx;
    private static TransactionTemplate tx;
    private static McqStudentExamService service;
    private static McqExamRepository exams;
    private static McqSubmissionRepository submissions;

    @BeforeAll static void boot() {
        ctx = new SpringApplicationBuilder(PcaExamAnalyzeApplication.class).run(
                "--spring.profiles.active=dev", "--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:studentlifecycle;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "--logging.level.root=ERROR");
        tx = new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class));
        service = ctx.getBean(McqStudentExamService.class);
        exams = ctx.getBean(McqExamRepository.class);
        submissions = ctx.getBean(McqSubmissionRepository.class);
    }
    @AfterAll static void close() { if (ctx != null) ctx.close(); }

    private static McqExam makeExam(boolean published) {
        return tx.execute(status -> {
            var e = new McqExam();
            e.setName("Student life cycle test");
            e.setSlug("student-journey-" + UUID.randomUUID());
            e.setExamYear(2027);
            e.setExamMonth("October");
            e.setPaperDriveUrl("https://drive.google.com/file/d/ABCDEF12345678/view");
            e.setOpenAt(Instant.now().minusSeconds(600));
            e.setCloseAt(Instant.now().plusSeconds(86400));
            e.setResultReleaseAt(Instant.now().plusSeconds(86400));
            e.setPublicationState(published ? McqExamPublicationState.PUBLISHED : McqExamPublicationState.DRAFT);
            e.setTotalQuestions(5);
            e.setDurationMinutes(60);
            for (int q = 1; q <= 5; q++) e.getAnswerKey().put(q, 1);
            return exams.save(e);
        });
    }
    private static ExamStudentDetailsForm student(String id) {
        var f = new ExamStudentDetailsForm();
        f.setEmail("student@example.test");
        f.setRegistrationId(id);
        f.setNic("200000000001");
        f.setFullName("Test Student");
        f.setBatch("2027 A/L");
        f.setSchool("Test School");
        f.setStream("Physical Science");
        f.setDistrict("Jaffna");
        return f;
    }

    @Test void startResumeAndWorkspacePreserveTheSameReceiptAndStudentAnswers() {
        var exam = makeExam(true);
        var student = student("JOURNEY01");
        var first = service.startOrResume(exam.getSlug(), student);
        assertNotNull(first.getId());
        assertTrue(first.getReceiptNumber().startsWith("PCA-"));
        assertEquals(McqSubmissionStatus.IN_PROGRESS, first.getStatus());
        assertEquals(first.getId(), service.startOrResume(exam.getSlug(), student).getId());
        assertEquals("IN_PROGRESS", service.workspace(first.getId(), student).status());
        assertEquals(5, service.workspace(first.getId(), student).totalQuestions());
        assertEquals(5, service.examDetails(exam.getSlug()).totalQuestions());
        assertTrue(service.examDetails(exam.getSlug()).canStart());
        assertFalse(service.alreadySubmitted(exam.getId(), student));
    }

    @Test void startRejectsUnpublishedScheduledClosedAndMissingStudentProfiles() {
        var exam = makeExam(true);
        assertThrows(IllegalStateException.class, () -> service.startOrResume(exam.getSlug(), null));
        assertThrows(IllegalArgumentException.class, () -> service.startOrResume("does-not-exist", student("NONE")));
        var draft = makeExam(false);
        assertThrows(IllegalArgumentException.class, () -> service.startOrResume(draft.getSlug(), student("NONE")));
        tx.executeWithoutResult(status -> exams.findById(exam.getId()).orElseThrow()
                .setOpenAt(Instant.now().plusSeconds(3600)));
        assertThrows(IllegalStateException.class, () -> service.startOrResume(exam.getSlug(), student("SCHEDULED")));
        tx.executeWithoutResult(status -> {
            var e = exams.findById(exam.getId()).orElseThrow();
            e.setOpenAt(Instant.now().minusSeconds(100));
            e.setManualClose(true);
        });
        assertThrows(IllegalStateException.class, () -> service.startOrResume(exam.getSlug(), student("CLOSED")));
    }

    @Test void bulkAndSingleAnswerSavesPreserveUnrelatedAnswersAndRejectInvalidInput() {
        var exam = makeExam(true);
        var student = student("JOURNEY02");
        long id = service.startOrResume(exam.getSlug(), student).getId();
        assertEquals(1, service.saveAnswer(id, 1, 1, student).answered());
        assertEquals(3, service.saveAnswers(id, Map.of(2, 2, 3, 1, 4, 3), student).answered());
        assertEquals(1, service.saveAnswers(id, Map.of(1, 1), Map.of(2, 10), student).answered());
        var workspace = service.workspace(id, student);
        assertEquals(4, workspace.answers().size());
        assertEquals(1, workspace.answers().get(1));
        assertEquals(2, workspace.answers().get(2));
        assertThrows(IllegalArgumentException.class, () -> service.saveAnswer(id, 0, 1, student));
        assertThrows(IllegalArgumentException.class, () -> service.saveAnswer(id, 1, 9, student));
        assertThrows(IllegalArgumentException.class, () -> service.saveAnswers(id, Map.of(9, 1), student));
        assertEquals(3, service.saveAnswer(id, 4, null, student).answered());
        assertEquals(3, service.workspace(id, student).answers().size());
    }

    @Test void finalSubmitReturnsSameResultOnRetryAndRejectsFutureAutosaves() {
        var exam = makeExam(true);
        var student = student("JOURNEY03");
        long id = service.startOrResume(exam.getSlug(), student).getId();
        var answers = Map.of(1,1,2,1,3,2,4,1,5,1);
        assertEquals(5, service.finish(id, student, answers, Map.of(1,15), 5).savedAnswers());
        assertEquals(5, service.finish(id, student, answers, Map.of(), 5).savedAnswers());
        assertTrue(service.alreadySubmitted(exam.getId(), student));
        assertTrue(service.submittedExamIds(student).contains(exam.getId()));
        assertThrows(IllegalStateException.class, () -> service.saveAnswer(id, 1, 2, student));
        assertThrows(IllegalStateException.class, () -> service.saveAnswers(id, Map.of(1, 2), student));
        var reloaded = service.startOrResume(exam.getSlug(), student);
        assertEquals(id, reloaded.getId());
        assertEquals(McqSubmissionStatus.SUBMITTED, reloaded.getStatus());
    }

    @Test void resultsRemainPrivateUntilTeacherReleasesThem() {
        var exam = makeExam(true);
        var student = student("JOURNEY04");
        long id = service.startOrResume(exam.getSlug(), student).getId();
        service.finish(id, student, Map.of(1,1,2,2,3,1,4,1,5,1), Map.of(), 5);
        var withheld = service.resultBySubmission(id, "journey04", "200000000001");
        assertFalse(withheld.released());
        assertNull(withheld.score());
        assertTrue(withheld.answers().isEmpty());
        tx.executeWithoutResult(status -> exams.findById(exam.getId()).orElseThrow().setResultsPublished(true));
        var released = service.resultBySubmission(id, "JOURNEY04", "200000000001");
        assertTrue(released.released());
        assertEquals(4, released.score());
        assertEquals(5, released.answers().size());
        assertEquals("CORRECT", released.answers().getFirst().state());
        assertEquals("INCORRECT", released.answers().get(1).state());
        assertEquals(1, service.resultsDashboard("JOURNEY04", "200000000001").releasedResults());
    }

    @Test void wrongRegistrationOrNicCannotReadAnotherStudentsResult() {
        var exam = makeExam(true);
        var student = student("JOURNEY05");
        long id = service.startOrResume(exam.getSlug(), student).getId();
        service.finish(id, student, Map.of(1,1), Map.of(), 1);
        assertThrows(IllegalStateException.class, () -> service.workspace(id, student("STRANGER")));
        assertThrows(IllegalStateException.class, () -> service.resultBySubmission(id, "STRANGER", "200000000001"));
        assertThrows(IllegalStateException.class, () -> service.resultBySubmission(id, "JOURNEY05", "200000000002"));
        assertThrows(IllegalArgumentException.class, () -> service.resultBySubmission(-1L, "JOURNEY05", "200000000001"));
        assertThrows(IllegalStateException.class, () -> service.result(exam.getSlug(), student("NORESULT")));
        assertThrows(IllegalStateException.class, () -> service.result(exam.getSlug(), (ExamStudentDetailsForm) null));
    }

    @Test void expiredWorkspaceGetsSafelyAutoSubmittedAndShowsNoEditableAnswers() {
        var exam = makeExam(true);
        var student = student("JOURNEY06");
        long id = service.startOrResume(exam.getSlug(), student).getId();
        tx.executeWithoutResult(status -> {
            exams.findById(exam.getId()).orElseThrow().setDurationMinutes(1);
            submissions.findById(id).orElseThrow().setStartedAt(Instant.now().minusSeconds(1000));
        });
        var workspace = service.workspace(id, student);
        assertTrue(workspace.autoSubmitted());
        assertEquals("SUBMITTED", workspace.status());
        assertTrue(workspace.answers().isEmpty());
        assertFalse(service.autoSubmitOverdueById(id));
    }

    @Test void normalAnswerSheetCannotExposePrivateQuestionImages() {
        var exam = makeExam(true);
        var student = student("JOURNEY07");
        long id = service.startOrResume(exam.getSlug(), student).getId();
        assertTrue(service.questionImage(id, 1, student).isEmpty());
        assertTrue(service.questionImage(id, 50, student).isEmpty());
        assertThrows(IllegalStateException.class, () -> service.questionImage(id, 1, student("STRANGER")));
        service.finish(id, student, Map.of(1,1), Map.of(), 1);
        assertTrue(service.resultQuestionImage(id, 1, student.getRegistrationId(), student.getNic()).isEmpty());
        assertTrue(service.resultQuestionImage(id, 1, null, student.getNic()).isEmpty());
    }

    @Test void invalidResultLookupIsRejectedBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.resultsDashboard(null, null));
        assertThrows(IllegalArgumentException.class, () -> service.resultsDashboard("x", "bad"));
        assertTrue(service.submittedExamIds(null).isEmpty());
        assertFalse(service.alreadySubmitted(-1L, null));
        assertThrows(IllegalStateException.class, () -> service.result("not-found", "", ""));
    }
}
