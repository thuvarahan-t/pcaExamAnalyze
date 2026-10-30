package com.example.pcaExamAnalyze.report;

import com.example.pcaExamAnalyze.repo.McqSubmissionRepository;
import com.example.pcaExamAnalyze.service.McqStudentExamService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import java.awt.Color;
import java.util.*;

/** Private, release-gated student report; never exposes other students' identities. */
@Service
public class StudentReportPdfService {
    private final McqStudentExamService students;
    private final McqSubmissionRepository submissions;
    private final ExamReportService reports;
    private final SpringTemplateEngine templates;
    private final StudentPerformanceReportService performance;
    private final PdfRenderGate gate;
    private final TransactionTemplate readOnly;

    public StudentReportPdfService(McqStudentExamService students, McqSubmissionRepository submissions,
                                   ExamReportService reports, SpringTemplateEngine templates,
                                   StudentPerformanceReportService performance, PdfRenderGate gate,
                                   PlatformTransactionManager transactions) {
        this.students = students; this.submissions = submissions; this.reports = reports; this.templates = templates;
        this.performance = performance; this.gate = gate;
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
    }

    public ExamReportPdfService.Pdf render(Long id, String registration, String nic) {
        return render(id, registration, nic, (stage, percent) -> {});
    }

    /** Not transactional: the database work runs in {@link #model}, so no connection is held while the PDF is painted. */
    public ExamReportPdfService.Pdf render(Long id, String registration, String nic,
            java.util.function.BiConsumer<String, Integer> progress) {
        return gate.run(() -> {
            org.w3c.dom.Document dom = page(id, registration, nic, progress);
            progress.accept("Writing your PDF", 90);
            try {
                byte[] bytes = PdfHtml.write(dom, Map.of("Inter", new int[]{400, 700}));
                progress.accept("Finishing your report", 98);
                return new ExamReportPdfService.Pdf(bytes, "PCA-Student-Report-" + id + ".pdf");
            } catch (java.io.IOException e) { throw new IllegalStateException("Could not create your report", e); }
        });
    }

    /** The model and HTML string go out of scope here, so only the DOM stays alive while the PDF is painted. */
    private org.w3c.dom.Document page(Long id, String registration, String nic,
            java.util.function.BiConsumer<String, Integer> progress) {
        Context ctx = readOnly.execute(status -> model(id, registration, nic, progress));
        progress.accept("Formatting your corrected answer sheet", 80);
        return PdfHtml.dom(templates.process("report/student-report", ctx));
    }

    private Context model(Long id, String registration, String nic,
            java.util.function.BiConsumer<String, Integer> progress) {
        progress.accept("Checking your released result", 3);
        var result = students.resultBySubmission(id, registration, nic);
        if (!result.released()) throw new IllegalStateException("The PDF is available after the teacher releases the result");
        var submission = submissions.findStudentSessionById(id).orElseThrow();
        var exam = submission.getExam();
        var analysis = reports.buildForStudent(exam.getId(), "", "", progress);
        var rows = result.answers();
        int score = (int) rows.stream().filter(a -> a.state().equals("CORRECT") || a.state().equals("FREE_MARK")).count();
        // Competition ranking: equal marks share a rank (1, 2, 2, 4), without time/name tie-breaks.
        int rank = 1 + (int) analysis.ranking().stream().filter(paper -> paper.correct() > score).count();
        long tiedPapers = analysis.ranking().stream().filter(paper -> paper.correct() == score).count();
        int free = (int) rows.stream().filter(a -> a.state().equals("FREE_MARK")).count();
        int wrong = (int) rows.stream().filter(a -> a.state().equals("INCORRECT")).count();
        int blank = (int) rows.stream().filter(a -> a.state().equals("UNANSWERED")).count();
        var slices = StudentReportCharts.slices(score - free, wrong, blank, free);
        var subjectAnalysis = performance.build(List.of(id), registration, nic,
                (stage, percent) -> progress.accept("Preparing your subject graphs", 77));
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("r", result);
        ctx.setVariable("questions", analysis.questions());
        ctx.setVariable("participants", analysis.meta().participants());
        ctx.setVariable("rank", rank);
        ctx.setVariable("sharedRank", tiedPapers > 1);
        ctx.setVariable("highestPercentage", analysis.summary().highestPct());
        ctx.setVariable("examAverage", analysis.summary().meanMarks());
        ctx.setVariable("score", score);
        ctx.setVariable("free", free);
        ctx.setVariable("slices", slices);
        ctx.setVariable("pieChart", StudentReportCharts.donut(slices));
        ctx.setVariable("subjects", subjectAnalysis.topics());
        ctx.setVariable("tagged", subjectAnalysis.tagged());
        ctx.setVariable("assessed", subjectAnalysis.assessed());
        ctx.setVariable("untagged", subjectAnalysis.untagged());
        ctx.setVariable("examTitle", StudentPerformanceReportService.label(result.examName(), 48, result.examName().length() > 90 ? 18 : 24, true));
        ctx.setVariable("studentLabel", StudentPerformanceReportService.label(result.studentName(), 54, 14, true));
        ctx.setVariable("period", exam.getExamMonth() + " " + exam.getExamYear());
        try (var in = new ClassPathResource("static/img/logo.png").getInputStream()) {
            ctx.setVariable("logo", "data:image/png;base64," + Base64.getEncoder().encodeToString(in.readAllBytes()));
        } catch (java.io.IOException ex) { throw new IllegalStateException("PCA logo is unavailable", ex); }
        ctx.setVariable("percentage", String.format(Locale.ENGLISH, "%.1f%%", score * 100.0 / rows.size()));
        ctx.setVariable("wrong", rows.stream().filter(a -> a.state().equals("INCORRECT")).count());
        ctx.setVariable("blank", rows.stream().filter(a -> a.state().equals("UNANSWERED")).count());
        ctx.setVariable("options", exam.getOptionsPerQuestion());
        ctx.setVariable("nameImg", TextImages.render(result.studentName(), true, 12, new Color(15, 35, 65)));
        ctx.setVariable("examImg", TextImages.render(result.examName(), true, 18, new Color(15, 35, 65)));
        ctx.setVariable("schoolImg", TextImages.render(result.school(), false, 10, new Color(15, 35, 65)));
        return ctx;
    }
}
