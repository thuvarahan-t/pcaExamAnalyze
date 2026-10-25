package com.example.pcaExamAnalyze.report;

import com.example.pcaExamAnalyze.domain.McqExam;
import com.example.pcaExamAnalyze.domain.McqSubmission;
import com.example.pcaExamAnalyze.domain.McqSubmissionStatus;
import com.example.pcaExamAnalyze.repo.McqQuestionMeta;
import com.example.pcaExamAnalyze.repo.McqSubmissionRepository;
import com.example.pcaExamAnalyze.service.McqExamQuestionService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentSubjectAnalysisTest {
    @Test void combinesDetailedTagsIntoSubjectsAndRendersOnlySubjectAnalysis() throws Exception {
        var submissions = mock(McqSubmissionRepository.class);
        var questions = mock(McqExamQuestionService.class);
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        var templates = new SpringTemplateEngine();
        templates.setTemplateResolver(resolver);
        var service = new StudentPerformanceReportService(submissions, questions, templates, new PdfRenderGate(1, 60),
                mock(org.springframework.transaction.PlatformTransactionManager.class));

        for (long id : List.of(1L, 2L)) {
            var exam = new McqExam();
            exam.setId(id);
            exam.setName("Physics " + id);
            exam.setExamMonth("October");
            exam.setExamYear(2026);
            exam.setTotalQuestions(6);
            exam.setResultsPublished(true);
            exam.setFreeMark(4, true);
            for (int q = 1; q <= 6; q++) exam.getAnswerKey().put(q, 1);
            var submission = new McqSubmission();
            submission.setId(id);
            submission.setExam(exam);
            submission.setRegistrationId("TEST");
            submission.setNic("200000000001");
            submission.setStudentName("Test Student");
            submission.setStatus(McqSubmissionStatus.SUBMITTED);
            submission.setSubmittedAt(Instant.parse("2026-10-01T00:00:00Z").plusSeconds(id));
            submission.getAnswers().putAll(Map.of(1, 1, 2, 2, 5, 1));
            when(submissions.findStudentSessionById(id)).thenReturn(Optional.of(submission));
            var mechanics = tag("Mechanics", "Motion graphs");
            when(questions.tagsByExam(id)).thenReturn(Map.of(
                    1, List.of(mechanics, tag("Mechanics", "Acceleration")),
                    2, List.of(mechanics),
                    3, List.of(tag("Measurement", "Dimensional equations")),
                    4, List.of(tag("Measurement", "Percentage error"))));
            when(questions.meta(id)).thenReturn(Map.of(5,
                    new McqQuestionMeta(5, null, null, null, "Measurement", "Detailed level",
                            "Detailed content", "Detailed outcome", null)));
        }

        var report = service.build(List.of(1L, 2L), "TEST", "200000000001", (stage, progress) -> {});
        assertEquals(List.of("Measurement", "Mechanics"), report.topics().stream().map(t -> t.name().text()).toList());
        assertEquals(10, report.assessed());
        assertEquals(8, report.tagged());
        assertEquals(2, report.untagged());
        assertEquals(2, report.free());
        for (var subject : report.topics()) {
            assertEquals(4, subject.total());
            assertEquals(2, subject.correct());
            assertEquals("50.0%", subject.accuracy());
            assertEquals(2, subject.wrong() + subject.blank());
        }
        assertEquals("Mechanics", report.papers().getFirst().answers().getFirst().topics().text());

        var pdf = service.render(List.of(1L, 2L), "TEST", "200000000001", (stage, progress) -> {});
        Path output = Path.of("target", "subject-report-review");
        Files.createDirectories(output);
        Files.write(output.resolve("subject-only.pdf"), pdf.bytes());
        try (var document = PDDocument.load(pdf.bytes())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Subject performance"));
            assertTrue(text.contains("Mechanics") && text.contains("Measurement"));
            for (String removed : List.of("Subject / syllabus area", "Competencies and levels",
                    "Content / topic breakdown", "Learning outcomes", "Motion graphs", "Dimensional equations")) {
                assertFalse(text.contains(removed), removed);
            }
            var renderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                ImageIO.write(renderer.renderImageWithDPI(page, 110), "png",
                        output.resolve("page-" + (page + 1) + ".png").toFile());
            }
        }
    }

    private static McqExamQuestionService.TagView tag(String subject, String outcome) {
        return new McqExamQuestionService.TagView(1L, 2L, subject, "Detailed competency",
                "Detailed level", List.of("Detailed content"), List.of(outcome));
    }
}
