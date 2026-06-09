package com.example.pcaExamAnalyze.service;

import com.example.pcaExamAnalyze.analysis.ReportData;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

/**
 * Renders a student's {@link ReportData} into a polished, printable PDF.
 *
 * Flow: Thymeleaf renders {@code report/pdf.html} to an HTML string, jsoup cleans it
 * into well-formed XHTML, and openhtmltopdf (PDFBox) turns that into PDF bytes. The
 * PCA logo is embedded as a base64 data-URI so the PDF is fully self-contained.
 */
@Service
public class PdfReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final SpringTemplateEngine templateEngine;
    /** Cached so we read + base64-encode the logo only once. */
    private volatile String logoDataUri;

    public PdfReportService(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public byte[] render(ReportData report, String studentName) {
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("report", report);
        ctx.setVariable("studentName", studentName);
        ctx.setVariable("generatedAt", LocalDate.now().format(DATE));
        ctx.setVariable("logo", logo());

        String html = templateEngine.process("report/pdf", ctx);

        Document jsoupDoc = Jsoup.parse(html);
        jsoupDoc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        org.w3c.dom.Document w3cDoc = new W3CDom().fromJsoup(jsoupDoc);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withW3cDocument(w3cDoc, "/");
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render PDF report", e);
        }
    }

    /** Base64 data-URI of the brand logo, read from the classpath once and cached. */
    private String logo() {
        String cached = logoDataUri;
        if (cached != null) {
            return cached;
        }
        try (InputStream in = new ClassPathResource("static/img/logo.png").getInputStream()) {
            String uri = "data:image/png;base64," + Base64.getEncoder().encodeToString(in.readAllBytes());
            logoDataUri = uri;
            return uri;
        } catch (IOException e) {
            // A missing logo should never break report generation.
            logoDataUri = "";
            return "";
        }
    }
}
