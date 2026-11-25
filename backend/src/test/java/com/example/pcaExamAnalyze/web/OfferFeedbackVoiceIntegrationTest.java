package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.PcaExamAnalyzeApplication;
import com.example.pcaExamAnalyze.service.FeedbackAudio;
import com.example.pcaExamAnalyze.service.GroqFeedbackTranscriber;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OfferFeedbackVoiceIntegrationTest {
    private static final String OFFER = "/exam/packages";
    private static final byte[] RECORDING = HexFormat.of().parseHex("4f676753000200000000000000000000000000000000000000000113"
        + "4f707573486561640101000080bb0000000000");
    private static ConfigurableApplicationContext context;
    private static GroqFeedbackTranscriber provider;
    private static String base;

    @BeforeAll static void boot() {
        context = new SpringApplicationBuilder(PcaExamAnalyzeApplication.class, TestProviders.class).run(
            "--spring.profiles.active=dev", "--server.port=0",
            "--spring.datasource.url=jdbc:h2:mem:offervoice;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "--pca.feedback.voice.groq.api-key=", "--pca.feedback.voice.enabled=false", "--logging.level.root=ERROR", "--debug=false");
        provider = context.getBean("stubGroq", GroqFeedbackTranscriber.class);
        base = "http://localhost:" + ((ServletWebServerApplicationContext) context).getWebServer().getPort();
    }

    @AfterAll static void close() {
        if (context != null) context.close();
    }

    @BeforeEach void stubProvider() {
        reset(provider);
        when(provider.available()).thenReturn(true);
        when(provider.transcribe(eq(FeedbackAudio.OGG), any(byte[].class))).thenReturn("PCA தேர்வு நன்றாக இருந்தது.");
    }

    @Test void acceptsOnlyCsrfProtectedAudioAndReturnsTextWithoutCaching() throws Exception {
        try (var student = client()) {
            var csrf = csrf(student);
            assertEquals(403, post(student, null, "audio/ogg", bytes(RECORDING)).statusCode());
            assertEquals(403, post(student, "bad-token", "audio/ogg", bytes(RECORDING)).statusCode());
            verify(provider, never()).transcribe(any(), any());

            var result = post(student, csrf, "audio/ogg;codecs=opus", bytes(RECORDING));
            assertEquals(200, result.statusCode());
            assertEquals("PCA தேர்வு நன்றாக இருந்தது.", JsonMapper.shared().readTree(result.body()).path("text").asString());
            assertEquals("no-store", result.headers().firstValue("cache-control").orElseThrow());
            verify(provider).transcribe(eq(FeedbackAudio.OGG), aryEqRecording());
        }
    }

    @Test void refusesMalformedOversizedAndUnsupportedUploadsBeforeAnyProviderTraffic() throws Exception {
        try (var student = client()) {
            var csrf = csrf(student);
            assertEquals(415, post(student, null, "multipart/form-data; boundary=test", bytes(RECORDING)).statusCode());
            assertEquals(415, post(student, csrf, "text/plain", bytes(RECORDING)).statusCode());
            assertEquals(400, post(student, csrf, "audio/webm", bytes(RECORDING)).statusCode());
            assertEquals(400, post(student, csrf, "audio/ogg", bytes(new byte[0])).statusCode());
            byte[] oversized = new byte[FeedbackAudio.MAX_BYTES + 1];
            assertEquals(413, post(student, csrf, "audio/ogg", bytes(oversized)).statusCode());
            var chunked = HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(oversized));
            assertEquals(413, post(student, csrf, "audio/ogg", chunked).statusCode());
            verify(provider, never()).transcribe(any(), any());
        }
    }

    @Test void allowsTypedOfferFeedbackWhenVoiceIsUnavailableOrFails() throws Exception {
        assertFalse(context.getBean("groqFeedbackTranscriber", GroqFeedbackTranscriber.class).available());
        try (var student = client()) {
            var csrf = csrf(student);
            when(provider.available()).thenReturn(false);
            assertEquals(503, post(student, csrf, "audio/ogg", bytes(RECORDING)).statusCode());
            verify(provider, never()).transcribe(any(), any());

            when(provider.available()).thenReturn(true);
            when(provider.transcribe(any(), any())).thenThrow(new GroqFeedbackTranscriber.Failed()).thenReturn("");
            assertEquals(502, post(student, csrf, "audio/ogg", bytes(RECORDING)).statusCode());
            assertEquals(422, post(student, csrf, "audio/ogg", bytes(RECORDING)).statusCode());
            var typed = HttpRequest.newBuilder(URI.create(base + OFFER + "/feedback"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-CSRF-TOKEN", csrf).POST(HttpRequest.BodyPublishers.ofString(
                    "name=Voice+Test&school=Central+College&district=Jaffna&comment=Typed+feedback")).build();
            assertEquals(302, student.send(typed, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertNotNull(Jsoup.parse(getOffer(student)).selectFirst("#student-feedback .showcase-message[role=status]"));
        }
    }

    @Test void boundsProviderCallsPerOfferSession() throws Exception {
        try (var student = client()) {
            var csrf = csrf(student);
            for (int i = 0; i < 5; i++) assertEquals(200, post(student, csrf, "audio/ogg", bytes(RECORDING)).statusCode());
            var limited = post(student, csrf, "audio/ogg", bytes(RECORDING));
            assertEquals(429, limited.statusCode());
            assertTrue(limited.headers().firstValue("retry-after").isPresent());
            verify(provider, times(5)).transcribe(eq(FeedbackAudio.OGG), any());
        }
    }

    private static byte[] aryEqRecording() { return org.mockito.AdditionalMatchers.aryEq(RECORDING); }

    private static HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .version(HttpClient.Version.HTTP_1_1).build();
    }

    private static String csrf(HttpClient student) throws Exception {
        return Jsoup.parse(getOffer(student)).selectFirst("meta[name=_csrf]").attr("content");
    }

    private static String getOffer(HttpClient student) throws Exception {
        var result = student.send(HttpRequest.newBuilder(URI.create(base + OFFER)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, result.statusCode());
        return result.body();
    }

    private static HttpRequest.BodyPublisher bytes(byte[] body) { return HttpRequest.BodyPublishers.ofByteArray(body); }

    private static HttpResponse<String> post(HttpClient student, String csrf, String type, HttpRequest.BodyPublisher body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + OfferFeedbackVoiceController.PATH)).header("Content-Type", type);
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        return student.send(request.POST(body).build(), HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestProviders {
        @Bean @Primary GroqFeedbackTranscriber stubGroq() { return mock(GroqFeedbackTranscriber.class); }
    }
}
