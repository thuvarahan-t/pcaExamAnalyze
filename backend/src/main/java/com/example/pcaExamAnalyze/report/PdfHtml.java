package com.example.pcaExamAnalyze.report;

import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Shared HTML-to-PDF steps. Callers build the DOM in a method of its own and pass only the DOM on, so the report model,
 * the HTML string and the jsoup tree (each holding every embedded image as base64) can be collected before rendering.
 */
final class PdfHtml {

    private PdfHtml() {}

    /** jsoup cleans the Thymeleaf output into well-formed XML, the form openhtmltopdf reads. */
    static org.w3c.dom.Document dom(String html) {
        org.jsoup.nodes.Document parsed = Jsoup.parse(html);
        parsed.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml);
        return new W3CDom().fromJsoup(parsed);
    }

    /** Paints the PDF with the given embedded fonts (family -> weights from report/fonts). */
    static byte[] write(org.w3c.dom.Document dom, Map<String, int[]> fonts) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            fonts.forEach((family, weights) -> {
                for (int weight : weights) font(builder, family, weight);
            });
            builder.withW3cDocument(dom, "/");
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
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
}
