package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.domain.McqSubmission;
import com.example.pcaExamAnalyze.report.*;
import com.example.pcaExamAnalyze.service.*;
import com.example.pcaExamAnalyze.web.dto.ExamStudentDetailsForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Controller-level HTTP contracts without a database or live network dependency. */
class StudentAnswerControllerContractTest {
    private McqAnswerWriter writer;
    private McqStudentExamService students;
    private ExamPortalController controller;
    private MockHttpSession session;

    @BeforeEach void setup() {
        writer = mock(McqAnswerWriter.class);
        students = mock(McqStudentExamService.class);
        controller = new ExamPortalController(mock(McqAdminService.class), students,
                mock(McqExamQuestionService.class), writer,
                mock(StudentReportPdfService.class), mock(StudentReportJobs.class),
                mock(StudentPerformanceReportService.class));
        session = new MockHttpSession();
        var details = new ExamStudentDetailsForm();
        details.setRegistrationId("BPF123456");
        details.setNic("200000000001");
        session.setAttribute(ExamDetailsCookie.SESSION_KEY, details);
        when(writer.owns(11L, "BPF123456", "200000000001")).thenReturn(true);
    }

    @Test void autosaveRejectsMissingIdentityAndForeignSubmissions() {
        session.removeAttribute(ExamDetailsCookie.SESSION_KEY);
        assertEquals(HttpStatus.FORBIDDEN, controller.saveAnswer(11L, 1, 1, false, session).getStatusCode());
        var details = new ExamStudentDetailsForm();
        details.setRegistrationId("BPF123456");
        details.setNic("200000000001");
        session.setAttribute(ExamDetailsCookie.SESSION_KEY, details);
        assertEquals(HttpStatus.FORBIDDEN, controller.saveAnswer(15L, 1, 1, false, session).getStatusCode());
        verify(writer, never()).save(anyLong(), anyInt(), anyInt());
    }

    @Test void autosaveUsesOwnershipCacheAndReturnsDefinitiveUnprocessableStatusForClosedPapers() {
        when(writer.save(11L, 1, 1)).thenReturn(true);
        assertEquals(HttpStatus.OK, controller.saveAnswer(11L, 1, 1, false, session).getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, controller.saveAnswer(11L, 1, null, false, session).getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, controller.saveAnswer(11L, 2, 2, false, session).getStatusCode());
        verify(writer, times(1)).owns(11L, "BPF123456", "200000000001");
        verify(writer).save(11L, 1, 1);
    }

    @Test void clearingAnswersReportsSuccessAndRefusal() {
        when(writer.clear(11L, 2)).thenReturn(true);
        assertEquals(HttpStatus.OK, controller.saveAnswer(11L, 2, null, true, session).getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, controller.saveAnswer(11L, 3, null, true, session).getStatusCode());
        verify(writer).clear(11L, 2);
        verify(writer).clear(11L, 3);
    }

    @Test void questionTimePayloadFiltersInvalidKeysAndValues() {
        assertEquals(HttpStatus.FORBIDDEN, controller.saveTimes(14L, Map.of("t1","10"), session).getStatusCode());
        var values = Map.of("t1","12", "t50","41", "t1000","99",
                "t5","non-numeric", "not-a-time","30", "t2","9999999");
        assertEquals(HttpStatus.OK, controller.saveTimes(11L, values, session).getStatusCode());
        verify(writer).saveTimes(eq(11L), eq(Map.of(1, 12, 50, 41)));
    }

    @Test void bulkAnswerPayloadSeparatesQuestionSelectionsFromTimers() {
        var status = new McqStudentExamService.SaveStatus(2, 50, 120);
        when(students.saveAnswers(eq(11L), anyMap(), anyMap(), any())).thenReturn(status);
        var response = controller.saveAnswers(11L, Map.of("q1","2","q2","4","t1","20","csrf","ignored"), session);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(status, response.getBody());
        verify(students).saveAnswers(eq(11L), eq(Map.of(1,2,2,4)), eq(Map.of(1,20)), any());
    }

    @Test void bulkAnswerValidationAndOptimisticConflictHaveStableResponseCodes() {
        assertEquals(HttpStatus.BAD_REQUEST, controller.saveAnswers(11L, Map.of("q1","bad"), session).getStatusCode());
        when(students.saveAnswers(eq(11L), anyMap(), anyMap(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(McqSubmission.class, 11L));
        assertEquals(HttpStatus.CONFLICT, controller.saveAnswers(11L, Map.of("q1","1"), session).getStatusCode());
    }

    @Test void finalSubmitReturnsDurableCountAndPreservesClientRedirectContract() {
        when(students.finish(eq(11L), any(), anyMap(), anyMap(), eq(2)))
                .thenReturn(new McqStudentExamService.SubmitResult("coverage-exam", 2));
        var response = controller.finishExam(11L,
                Map.of("q1","1","q2","2","t1","30","expected","2","unknown","3"),session);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) response.getBody();
        assertEquals("SUBMITTED", body.get("status"));
        assertEquals(2, body.get("saved"));
        assertEquals("/exam/p/coverage-exam/result", body.get("redirect"));
        verify(students).finish(eq(11L), any(), eq(Map.of(1,1,2,2)), eq(Map.of(1,30)), eq(2));
    }

    @Test void finalSubmitReturnsConflictAndBadRequestWithoutClaimingSuccess() {
        when(students.finish(eq(11L), any(), anyMap(), anyMap(), anyInt()))
                .thenThrow(new ObjectOptimisticLockingFailureException(McqSubmission.class, 11L))
                .thenThrow(new IllegalStateException("Student record missing"));
        assertEquals(HttpStatus.CONFLICT, controller.finishExam(11L, Map.of("q1","1"), session).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.finishExam(11L, Map.of("q1","1"), session).getStatusCode());
    }

    @Test void legacyFormSubmitRetriesOptimisticConflictBeforeRedirecting() {
        when(students.submit(eq(11L), any(), anyMap(), anyMap()))
                .thenThrow(new ObjectOptimisticLockingFailureException(McqSubmission.class, 11L))
                .thenReturn("coverage-exam");
        var redirect = controller.submitExam(11L, Map.of("answersSent","1","q1","1","t1","10"),
                session, new RedirectAttributesModelMap());
        assertEquals("redirect:/exam/p/coverage-exam/result", redirect);
        verify(students, times(2)).submit(eq(11L), any(), eq(Map.of(1,1)), eq(Map.of(1,10)));
    }

    @Test void clearDetailsInvalidatesSavedIdentityAndBrowserCookie() {
        var request = new MockHttpServletRequest("POST", "/exam/details/clear");
        var response = new MockHttpServletResponse();
        assertEquals("redirect:/exam", controller.clearStudentDetails(session, request, response));
        assertNull(session.getAttribute(ExamDetailsCookie.SESSION_KEY));
        assertEquals(0, response.getCookies()[0].getMaxAge());
    }
}
