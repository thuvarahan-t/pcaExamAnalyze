package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.PcaExamAnalyzeApplication;
import com.example.pcaExamAnalyze.domain.McqExam;
import com.example.pcaExamAnalyze.domain.McqExamPublicationState;
import com.example.pcaExamAnalyze.domain.McqSheetType;
import com.example.pcaExamAnalyze.repo.McqExamRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class ExamPwaIntegrationTest {
    private static ConfigurableApplicationContext app;
    private static String base;

    @BeforeAll static void boot() {
        app = new SpringApplicationBuilder(PcaExamAnalyzeApplication.class).run(
                "--spring.profiles.active=dev", "--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:exampwa;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "--pca.storage.r2.account-id=", "--debug=false", "--logging.level.root=ERROR");
        base = "http://localhost:" + ((ServletWebServerApplicationContext) app).getWebServer().getPort();
    }

    @AfterAll static void close() {
        if (app != null) app.close();
    }

    @Test void pwaFilesArePublicAndTeacherPagesStayProtected() throws Exception {
        var browser = browser();
        var manifest = get(browser, "/exam.webmanifest");
        assertEquals(200, manifest.statusCode());
        assertTrue(header(manifest, "Content-Type").startsWith("application/manifest+json"));
        assertTrue(manifest.body().contains("\"start_url\": \"/exam\""));
        var worker = get(browser, "/exam-sw.js");
        assertEquals(200, worker.statusCode());
        assertTrue(header(worker, "Content-Type").contains("javascript"));
        assertEquals("no-cache", header(worker, "Cache-Control"));
        var offline = get(browser, "/exam-offline.html");
        assertEquals(200, offline.statusCode());
        assertTrue(offline.body().contains("Connection unavailable"));
        for (int size : List.of(192, 512)) {
            var icon = browser.send(request("/img/pca-exam-" + size + ".png?v=1").build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, icon.statusCode());
            assertEquals("image/png", header(icon, "Content-Type"));
            var image = ImageIO.read(new ByteArrayInputStream(icon.body()));
            assertEquals(size, image.getWidth());
            assertEquals(size, image.getHeight());
        }
        for (String path : List.of("/exam/admin", "/exam/admin/packages")) {
            assertEquals(302, get(browser, path).statusCode(), path);
        }
    }

    @Test void actualStudentRoutesAndBothWorkspacesUseTheSameManifest() throws Exception {
        var browser = browser();
        for (String path : List.of("/exam", "/exam/packages")) {
            var page = assertPwaHead(get(browser, path));
            assertEquals("Install PCA Exam", page.selectFirst("button[data-pca-install]").text());
            assertTrue(page.selectFirst("dialog[data-pca-install-guide] [data-pca-safari]").text().contains("Open as Web App"));
        }
        var details = assertPwaHead(get(browser, "/exam/details"));
        assertNull(details.selectFirst("button[data-pca-install]"));
        assertEquals(302, post(browser, "/exam/details", csrf(details),
                "registrationId=PWA001&nic=200000000001&fullName=Test+Student&email=student%40example.test"
                        + "&batch=2027+A%2FL&school=Test+School&stream=Physical+Science&district=Jaffna").statusCode());

        String classicSlug = null;
        String classicSession = null;
        for (McqSheetType sheetType : McqSheetType.values()) {
            var slug = fixture(sheetType);
            var paper = assertPwaHead(get(browser, "/exam/p/" + slug));
            assertPwaHead(get(browser, "/exam/p/" + slug + "/result-lookup"));
            var started = post(browser, "/exam/p/" + slug + "/start", csrf(paper), "");
            assertEquals(302, started.statusCode(), started.body());
            String workspace = location(started);
            assertTrue(workspace.startsWith("/exam/session/"), workspace);
            var page = assertPwaHead(get(browser, workspace));
            assertEquals(sheetType == McqSheetType.QUESTION_IMAGES, page.body().hasClass("step-body"));
            if (sheetType == McqSheetType.ANSWER_SHEET) {
                var submitted = post(browser, workspace + "/submit", csrf(page), "answersSent=1&q1=1");
                assertEquals(302, submitted.statusCode());
                assertEquals("/exam/p/" + slug + "/result", location(submitted));
                classicSlug = slug;
                classicSession = workspace;
            }
        }

        assertPwaHead(get(browser, "/exam/p/" + classicSlug + "/result"));
        var lookup = assertPwaHead(get(browser, "/exam/result"));
        assertEquals(302, post(browser, "/exam/result", csrf(lookup), "registrationId=PWA001&nic=200000000001").statusCode());
        assertPwaHead(get(browser, "/exam/results"));
        assertPwaHead(get(browser, classicSession.replace("/session/", "/results/")));
    }

    private static String fixture(McqSheetType sheetType) {
        var exam = new McqExam();
        exam.setName("PWA test paper " + sheetType.name());
        exam.setSlug(sheetType.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        exam.setExamYear(2027);
        exam.setExamMonth("October");
        exam.setPaperDriveUrl("https://drive.google.com/file/d/ABCDEF12345678/view");
        exam.setOpenAt(Instant.now().minusSeconds(60));
        exam.setCloseAt(Instant.now().plusSeconds(3600));
        exam.setResultReleaseAt(Instant.now().plusSeconds(3600));
        exam.setPublicationState(McqExamPublicationState.PUBLISHED);
        exam.setDurationMinutes(30);
        exam.setTotalQuestions(1);
        exam.setSheetType(sheetType);
        exam.getAnswerKey().put(1, 1);
        return app.getBean(McqExamRepository.class).save(exam).getSlug();
    }

    private static Document assertPwaHead(HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), response.uri().toString());
        var page = Jsoup.parse(response.body());
        assertEquals(List.of("/exam.webmanifest"), page.select("head link[rel=manifest]").eachAttr("href"));
        assertEquals(List.of("/exam"), page.select("head script[data-worker-url='/exam-sw.js']").eachAttr("data-worker-scope"));
        assertEquals(1, page.select("head link[rel=apple-touch-icon]").size());
        return page;
    }

    private static String csrf(Document page) {
        return page.selectFirst("input[name=_csrf]").val();
    }

    private static String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElseThrow();
    }

    private static String location(HttpResponse<?> response) {
        return URI.create(header(response, "Location")).getPath();
    }

    private static HttpClient browser() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    }

    private static HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(base + path));
    }

    private static HttpResponse<String> get(HttpClient browser, String path) throws Exception {
        return browser.send(request(path).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(HttpClient browser, String path, String csrf, String body) throws Exception {
        return browser.send(request(path).header("X-CSRF-TOKEN", csrf)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
