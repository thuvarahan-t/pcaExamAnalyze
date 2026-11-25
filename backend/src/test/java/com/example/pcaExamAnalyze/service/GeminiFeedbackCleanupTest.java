package com.example.pcaExamAnalyze.service;

import com.example.pcaExamAnalyze.web.OfferFeedbackVoiceController;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GeminiFeedbackCleanupTest {
    private static final String GROQ_TEXT = "அருள் PCA-Exam இல் 87.5% பெற்றார் நல்ல வகுப்பு";
    private static final String CLEAN_TEXT = "அருள் PCA-Exam இல் 87.5% பெற்றார். நல்ல வகுப்பு!";
    private static final String GROQ_PATH = "/openai/v1/audio/transcriptions";
    private static final String GEMINI_PATH = "/v1beta/models/gemini-test:generateContent";
    private HttpServer providers;
    private URI base;
    private volatile JsonNode geminiRequest;
    private volatile String geminiKey;
    private volatile String groqRequest;
    private volatile byte[] geminiResponse = finished(CLEAN_TEXT);
    private volatile int geminiStatus = 200;
    private volatile CountDownLatch stall;
    private final AtomicInteger geminiRequests = new AtomicInteger();

    @BeforeEach void start() throws IOException {
        providers = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        providers.createContext(GROQ_PATH, this::groq);
        providers.createContext(GEMINI_PATH, this::gemini);
        providers.start();
        base = URI.create("http://127.0.0.1:" + providers.getAddress().getPort());
    }

    @AfterEach void stop() {
        if (stall != null) stall.countDown();
        providers.stop(0);
    }

    @Test void sendsOnlyGroqTranscriptToGeminiAndReturnsDefensibleCleanup() {
        var groq = new GroqFeedbackTranscriber(true, "groq-test", "whisper-large-v3",
            FeedbackProviderClient.http(Duration.ofSeconds(3)), base.resolve(GROQ_PATH));
        var controller = new OfferFeedbackVoiceController(groq, cleaner(true, "gemini-key", "gemini-test", Duration.ofSeconds(3)));
        var request = new MockHttpServletRequest("POST", OfferFeedbackVoiceController.PATH);
        request.setContentType("audio/ogg");
        request.setContent(FeedbackAudioTest.oggOpus());

        var result = controller.transcribe(request);
        assertEquals(200, result.getStatusCode().value());
        assertEquals(CLEAN_TEXT, assertInstanceOf(OfferFeedbackVoiceController.Transcript.class, result.getBody()).text());
        assertTrue(groqRequest.contains("OpusHead"));
        assertEquals("gemini-key", geminiKey);
        assertEquals(1, geminiRequests.get());
        var contents = geminiRequest.path("contents");
        assertEquals(1, contents.size());
        var part = contents.path(0).path("parts").path(0);
        assertEquals(1, part.size());
        assertEquals(GROQ_TEXT, part.path("text").asString());
        assertFalse(geminiRequest.toString().contains("OpusHead"));
        assertEquals("application/json", geminiRequest.path("generationConfig").path("responseMimeType").asString());
    }

    @Test void skipsGeminiWhenDisabledOrMissingKeyOrModel() {
        assertEquals(GROQ_TEXT, cleaner(false, "gemini-key", "gemini-test", Duration.ofSeconds(3)).clean(GROQ_TEXT));
        assertEquals(GROQ_TEXT, cleaner(true, "", "gemini-test", Duration.ofSeconds(3)).clean(GROQ_TEXT));
        assertEquals(GROQ_TEXT, cleaner(true, "gemini-key", "", Duration.ofSeconds(3)).clean(GROQ_TEXT));
        assertEquals(0, geminiRequests.get());
    }

    @Test void keepsRawTextWhenNamesNumbersEnglishOrMeaningChange() {
        var cleaner = cleaner(true, "gemini-key", "gemini-test", Duration.ofSeconds(3));
        for (String rewrite : List.of(CLEAN_TEXT.replace("அருள்", "அருண்"), CLEAN_TEXT.replace("PCA-Exam", "PCA Exam"),
                CLEAN_TEXT.replace("87.5%", "87,5%"), CLEAN_TEXT.replace("87.5%", "87.5"),
                "The class was good.", CLEAN_TEXT + " எல்லோருக்கும் பரிந்துரை செய்கிறேன்", "")) {
            geminiResponse = finished(rewrite);
            assertEquals(GROQ_TEXT, cleaner.clean(GROQ_TEXT));
        }
    }

    @Test void allowsEquivalentTamilSpellingWithPunctuationAndSpacing() {
        var cleaner = cleaner(true, "gemini-key", "gemini-test", Duration.ofSeconds(3));
        String original = Normalizer.normalize("கோபி 2.5% சொன்னார்", Normalizer.Form.NFD);
        String edited = "கோபி 2.5 % சொன்னார்.";
        geminiResponse = finished(edited);
        assertEquals(edited, cleaner.clean(original));
    }

    @Test void keepsRawTextOnBlockedInvalidOrUnavailableCleanup() {
        var cleaner = cleaner(true, "gemini-key", "gemini-test", Duration.ofSeconds(3));
        for (String invalid : List.of("<html>", "{}", "{} {}", "{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}")) {
            geminiResponse = invalid.getBytes(StandardCharsets.UTF_8);
            assertEquals(GROQ_TEXT, cleaner.clean(GROQ_TEXT));
        }
        geminiResponse = candidate("{\"transcript\":\"valid value\"} {}", "STOP");
        assertEquals(GROQ_TEXT, cleaner.clean(GROQ_TEXT));
        geminiResponse = finished(CLEAN_TEXT);
        geminiStatus = 503;
        assertEquals(GROQ_TEXT, cleaner.clean(GROQ_TEXT));
    }

    @Test void keepsRawTextWhenTheGeminiResponseTimesOut() {
        stall = new CountDownLatch(1);
        var cleaner = cleaner(true, "gemini-key", "gemini-test", Duration.ofMillis(500));
        assertEquals(GROQ_TEXT, cleaner.clean(GROQ_TEXT));
        assertEquals(1, geminiRequests.get());
    }

    private GeminiFeedbackCleanup cleaner(boolean enabled, String key, String model, Duration timeout) {
        return new GeminiFeedbackCleanup(enabled, key, model, FeedbackProviderClient.http(timeout), base.resolve("/v1beta/models/"));
    }

    private static byte[] finished(String text) {
        return candidate(FeedbackProviderClient.JSON.writeValueAsString(Map.of("transcript", text)), "STOP");
    }

    private static byte[] candidate(String text, String finishReason) {
        return FeedbackProviderClient.JSON.writeValueAsBytes(Map.of("candidates", List.of(Map.of(
            "finishReason", finishReason, "content", Map.of("parts", List.of(Map.of("text", text)))))));
    }

    private void groq(HttpExchange exchange) throws IOException {
        try (exchange) {
            groqRequest = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] body = FeedbackProviderClient.JSON.writeValueAsBytes(Map.of("text", GROQ_TEXT));
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private void gemini(HttpExchange exchange) throws IOException {
        try (exchange) {
            geminiRequests.incrementAndGet();
            geminiKey = exchange.getRequestHeaders().getFirst("x-goog-api-key");
            geminiRequest = FeedbackProviderClient.JSON.readTree(exchange.getRequestBody().readAllBytes());
            exchange.sendResponseHeaders(geminiStatus, geminiResponse.length);
            if (stall != null) stall.await(5, TimeUnit.SECONDS);
            exchange.getResponseBody().write(geminiResponse);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
