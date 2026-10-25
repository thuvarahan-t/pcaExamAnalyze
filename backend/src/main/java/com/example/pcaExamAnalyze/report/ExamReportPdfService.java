package com.example.pcaExamAnalyze.report;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders the exam analysis report: Thymeleaf fills {@code report/exam-report.html}, jsoup turns it into well-formed
 * XML and openhtmltopdf (PDFBox) paints the PDF. Inter and Sora are embedded so the document looks the same everywhere.
 */
@Service
public class ExamReportPdfService {

    private static final int[] INTER_WEIGHTS = {400, 500, 600, 700, 800};
    private static final int[] SORA_WEIGHTS = {400, 600, 700, 800};

    private static final Logger log = LoggerFactory.getLogger(ExamReportPdfService.class);

    private final SpringTemplateEngine templates;
    private final ExamReportService reports;
    private final PdfRenderGate gate;
    private volatile String logo;
    private volatile String banner;

    public ExamReportPdfService(SpringTemplateEngine templates, ExamReportService reports, PdfRenderGate gate) {
        this.templates = templates;
        this.reports = reports;
        this.gate = gate;
    }

    public record Pdf(byte[] bytes, String fileName) {}

    public Pdf render(Long examId) {
        return render(examId, (stage, percentage) -> { });
    }

    public Pdf render(Long examId, BiConsumer<String, Integer> progress) {
        return gate.run(() -> write(examId, progress));
    }

    private Pdf write(Long examId, BiConsumer<String, Integer> progress) {
        long t0 = System.nanoTime();
        Page page = page(examId, progress);
        long t1 = System.nanoTime();
        progress.accept("Writing the PDF", 90);
        try {
            byte[] bytes = PdfHtml.write(page.dom(), Map.of("Inter", INTER_WEIGHTS, "Sora", SORA_WEIGHTS));
            long t2 = System.nanoTime();
            log.info("Exam report {}: analysis and layout {} ms, pdf {} ms, {} KB",
                    examId, (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, bytes.length / 1024);
            return new Pdf(bytes, page.fileName());
        } catch (IOException ex) {
            throw new IllegalStateException("Could not render the exam report", ex);
        }
    }

    private record Page(org.w3c.dom.Document dom, String fileName) {}

    /** The report model and HTML string go out of scope here, so only the DOM stays alive while the PDF is painted. */
    private Page page(Long examId, BiConsumer<String, Integer> progress) {
        ExamReport report = reports.build(examId, logo(), banner(), progress);
        progress.accept("Designing the pages", 80);
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("r", report);
        return new Page(PdfHtml.dom(templates.process("report/exam-report", ctx)), fileName(report));
    }

    private static String fileName(ExamReport report) {
        String base = report.meta().examName().replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "");
        if (base.length() > 60) base = base.substring(0, 60);
        return "PCA-Exam-Report-" + (base.isEmpty() ? "exam" : base) + ".pdf";
    }

    private String logo() {
        String cached = logo;
        if (cached != null) return cached;
        try (InputStream in = new ClassPathResource("static/img/logo.png").getInputStream()) {
            cached = "data:image/png;base64," + Base64.getEncoder().encodeToString(in.readAllBytes());
        } catch (IOException ex) {
            cached = "";
        }
        return logo = cached;
    }

    private String banner() {
        String cached = banner;
        if (cached == null) banner = cached = ReportBanner.dataUri(1240, 760);
        return cached;
    }
}
