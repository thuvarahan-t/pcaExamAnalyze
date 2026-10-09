package com.example.pcaExamAnalyze.report;

import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Locale;
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
    private volatile String logo;
    private volatile String banner;

    public ExamReportPdfService(SpringTemplateEngine templates, ExamReportService reports) {
        this.templates = templates;
        this.reports = reports;
    }

    public record Pdf(byte[] bytes, String fileName) {}

    public Pdf render(Long examId) {
        long t0 = System.nanoTime();
        ExamReport report = reports.build(examId, logo(), banner());
        long t1 = System.nanoTime();
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("r", report);
        String html = templates.process("report/exam-report", ctx);
        long t2 = System.nanoTime();

        Document parsed = Jsoup.parse(html);
        parsed.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        org.w3c.dom.Document w3c = new W3CDom().fromJsoup(parsed);
        long t3 = System.nanoTime();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            for (int w : INTER_WEIGHTS) font(builder, "Inter", w);
            for (int w : SORA_WEIGHTS) font(builder, "Sora", w);
            builder.withW3cDocument(w3c, "/");
            builder.toStream(out);
            builder.run();
            long t4 = System.nanoTime();
            log.info("Exam report {}: analysis {} ms, template {} ms, dom {} ms, pdf {} ms, {} KB",
                    examId, (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t3 - t2) / 1_000_000, (t4 - t3) / 1_000_000, out.size() / 1024);
            return new Pdf(out.toByteArray(), fileName(report));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not render the exam report", ex);
        }
    }

    private static void font(PdfRendererBuilder builder, String family, int weight) {
        String path = "report/fonts/" + family + "-" + weight + ".ttf";
        FSSupplier<InputStream> supplier = () -> {
            try {
                return new ClassPathResource(path).getInputStream();
            } catch (IOException ex) {
                throw new IllegalStateException("Missing report font " + path, ex);
            }
        };
        builder.useFont(supplier, family, weight, BaseRendererBuilder.FontStyle.NORMAL, true);
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
