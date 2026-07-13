package com.example.pcaExamAnalyze;

import com.example.pcaExamAnalyze.domain.McqExam;
import com.example.pcaExamAnalyze.domain.McqExamPublicationState;
import com.example.pcaExamAnalyze.repo.McqExamRepository;
import com.example.pcaExamAnalyze.repo.McqSubmissionRepository;
import com.example.pcaExamAnalyze.web.dto.ExamStudentDetailsForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class McqAdminAnalyticsViewTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private McqExamRepository exams;
    @Autowired private McqSubmissionRepository submissions;

    @Test
    @WithMockUser(username = "teacher", roles = "TEACHER")
    void analyticsPageRendersChartsDetailsAndCsvAction() throws Exception {
        McqExam exam = new McqExam();
        exam.setName("Analytics Render Test");
        exam.setSlug("analytics-render-test");
        exam.setExamYear(2026);
        exam.setExamMonth("July");
        exam.setPaperDriveUrl("https://drive.google.com/file/d/test/view");
        exam.setTotalQuestions(20);
        exam.setOptionsPerQuestion(5);
        exam.setOpenAt(Instant.now().minusSeconds(3600));
        exam.setCloseAt(Instant.now().plusSeconds(3600));
        exam.setResultReleaseAt(Instant.now().plusSeconds(7200));
        exam.setPublicationState(McqExamPublicationState.PUBLISHED);
        exam.setDurationMinutes(40);
        exam.setEligibleBatches(new LinkedHashSet<>(java.util.List.of("2026 A/L")));
        exam.setEligibleStreams(new LinkedHashSet<>(java.util.List.of("Bio Science", "Physical Science")));
        LinkedHashMap<Integer, Integer> key = new LinkedHashMap<>();
        for (int question = 1; question <= 20; question++) key.put(question, 1);
        exam.setAnswerKey(key);
        exam = exams.saveAndFlush(exam);

        mockMvc.perform(get("/exam/admin/exams/{id}/analytics", exam.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Score Distribution")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Question Correctness")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Export Results CSV")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Analytics Render Test")));
    }

    @Test
    @WithMockUser(username = "teacher", roles = "TEACHER")
    void teacherCanSaveMultipleCorrectOptionsForOneQuestion() throws Exception {
        mockMvc.perform(post("/exam/admin/exams").with(csrf())
                        .param("name", "Multiple Correct Answer Test")
                        .param("batch", "2026 A/L")
                        .param("totalQuestions", "1")
                        .param("examYear", "2026")
                        .param("examMonth", "July")
                        .param("paperDriveUrl", "https://drive.google.com/file/d/test/view")
                        .param("openAt", "2026-07-13T10:00")
                        .param("closeAt", "2026-07-14T10:00")
                        .param("resultReleaseAt", "2026-07-15T10:00")
                        .param("durationMinutes", "30")
                        .param("answers[1]", "1", "3")
                .param("action", "draft"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/exam/admin/exams"));

        McqExam saved = exams.findAllByOrderByCreatedAtDesc().stream()
                .filter(exam -> exam.getName().equals("Multiple Correct Answer Test"))
                .findFirst().orElseThrow();
        assertEquals(java.util.Set.of(1, 3), saved.correctOptions(1));

        mockMvc.perform(post("/exam/admin/exams").with(csrf())
                        .param("name", "Published Redirect Test")
                        .param("batch", "2026 A/L")
                        .param("totalQuestions", "1")
                        .param("examYear", "2026")
                        .param("examMonth", "July")
                        .param("paperDriveUrl", "https://drive.google.com/file/d/publish-test/view")
                        .param("openAt", "2026-07-13T10:00")
                        .param("closeAt", "2026-07-14T10:00")
                        .param("resultReleaseAt", "2026-07-15T10:00")
                        .param("durationMinutes", "30")
                        .param("answers[1]", "2")
                        .param("action", "publish"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/exam/admin/exams"));

        McqExam published = exams.findAllByOrderByCreatedAtDesc().stream()
                .filter(exam -> exam.getName().equals("Published Redirect Test"))
                .findFirst().orElseThrow();
        assertEquals(McqExamPublicationState.PUBLISHED, published.getPublicationState());
        assertEquals("Published Redirect Test", exams.findAllByOrderByCreatedAtDesc().getFirst().getName());
    }

    @Test
    void studentCanOpenAnswerSubmitAndViewResultFlow() throws Exception {
        McqExam exam = new McqExam();
        exam.setName("Student Flow Render Test");
        exam.setSlug("student-flow-render-test");
        exam.setExamYear(2026);
        exam.setExamMonth("July");
        exam.setPaperDriveUrl("https://drive.google.com/file/d/1234567890test/preview");
        exam.setTotalQuestions(20);
        exam.setOptionsPerQuestion(5);
        exam.setOpenAt(Instant.now().minusSeconds(3600));
        exam.setCloseAt(Instant.now().plusSeconds(3600));
        exam.setResultReleaseAt(Instant.now().plusSeconds(7200));
        exam.setPublicationState(McqExamPublicationState.PUBLISHED);
        exam.setDurationMinutes(40);
        exam.setEligibleBatches(new LinkedHashSet<>(java.util.List.of("2026 A/L")));
        exam.setEligibleStreams(new LinkedHashSet<>(java.util.List.of("Bio Science", "Physical Science")));
        LinkedHashMap<Integer, Integer> key = new LinkedHashMap<>();
        for (int question = 1; question <= 20; question++) key.put(question, 2);
        exam.setAnswerKey(key);
        exam = exams.saveAndFlush(exam);

        ExamStudentDetailsForm details = new ExamStudentDetailsForm();
        details.setEmail("flow@example.com");
        details.setRegistrationId("FLOW-001");
        details.setNic("200012345678");
        details.setFullName("Flow Test Student");
        details.setBatch("2026 A/L");
        details.setSchool("PCA Test School");
        details.setStream("Bio Science");
        details.setDistrict("Ampara");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("mcqStudentDetails", details);

        mockMvc.perform(get("/exam/p/{slug}", exam.getSlug()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paper details")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Start / Continue MCQ Paper")));

        String location = mockMvc.perform(post("/exam/p/{slug}/start", exam.getSlug()).session(session).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertTrue(location != null && location.startsWith("/exam/session/"));
        long submissionId = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));

        mockMvc.perform(get("/exam/session/{id}", submissionId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Question Paper")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("MCQ Answer Sheet")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("q20o5")));

        mockMvc.perform(post("/exam/session/{id}/answer", submissionId).session(session).with(csrf())
                        .param("question", "1").param("option", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"answered\":1")));

        mockMvc.perform(post("/exam/session/{id}/submit", submissionId).session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/exam/p/{slug}/result", exam.getSlug()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paper submitted")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Result is not released yet")));

        mockMvc.perform(get("/exam/p/{slug}/result-lookup", exam.getSlug()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Registration ID")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("NIC Number")));
        mockMvc.perform(post("/exam/p/{slug}/result-lookup", exam.getSlug()).with(csrf())
                        .param("registrationId", "FLOW-001").param("nic", "200012345678"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paper submitted")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("FLOW-001")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("200012345678")));
        mockMvc.perform(post("/exam/p/{slug}/result-lookup", exam.getSlug()).with(csrf())
                        .param("registrationId", "FLOW-001").param("nic", "200012345679"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("We could not find a matching result")));

        mockMvc.perform(post("/exam/result").session(session).with(csrf())
                        .param("registrationId", "FLOW-001").param("nic", "200012345678"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/exam/results"));
        mockMvc.perform(get("/exam/result").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"FLOW-001\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"200012345678\"")));
        mockMvc.perform(get("/exam/results").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("My Results")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Last 5 exam percentages")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("RESULTS COMING SOON")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Student Flow Render Test")));
        mockMvc.perform(get("/exam/results/{id}", submissionId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Results coming soon")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PCA Test School")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paper preview")));

        mockMvc.perform(get("/exam/admin/submissions/{id}", submissionId)
                        .with(user("teacher").roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Student Details")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Submission Details")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("200012345678")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PCA Test School")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ampara")));

        mockMvc.perform(get("/exam/admin/submissions")
                        .with(user("teacher").roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Delete submission")));

        exam.setResultsPublished(true);
        exam.setResultReleaseAt(Instant.now().minusSeconds(1));
        exams.saveAndFlush(exam);
        mockMvc.perform(get("/exam/p/{slug}/result", exam.getSlug()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Corrected MCQ Sheet")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("result-omr-sheet")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1/20")));
        mockMvc.perform(get("/exam/results").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("RESULT RELEASED")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1/20")));
        mockMvc.perform(get("/exam/results/{id}", submissionId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Corrected MCQ Sheet")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("200012345678")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ampara")));

        mockMvc.perform(post("/exam/admin/submissions/{id}/delete", submissionId)
                        .with(user("teacher").roles("TEACHER")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertFalse(submissions.existsById(submissionId));

        mockMvc.perform(post("/exam/admin/exams/{id}/delete", exam.getId())
                        .with(user("teacher").roles("TEACHER")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertFalse(exams.existsById(exam.getId()));
    }
}
