package com.example.pcaExamAnalyze.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.junit.jupiter.api.Assertions.*;

class GeminiFeedbackTranscriberTest {
    private static final String MODEL = "gemini-3.5-flash-lite";
    private static final String TRANSCRIPT = "இண்டைக்கு tuition-க்கு வாறன். The class was good.";
    private static final String FALLBACK_TRANSCRIPT = "class-க்கு வாறன்.";
    private static final byte[] AUDIO = {1, 2, -1, 0, 127};
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private HttpServer provider;
    private URI models;
    private URI groqEndpoint;
    private volatile byte[] response = transcript(TRANSCRIPT);
    private volatile int status = 200;
    private volatile JsonNode request;
    private volatile String key;
    private volatile String query;
    private volatile CountDownLatch stall;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger groqCalls = new AtomicInteger();
    private volatile int groqStatus = 200;
    private volatile String groqRequest;
    private volatile String groqKey;

    @BeforeEach void start() throws IOException {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/v1beta/models/" + MODEL + ":generateContent", this::respond);
        provider.createContext("/openai/v1/audio/transcriptions", this::respondGroq);
        provider.start();
        URI base = URI.create("http://127.0.0.1:" + provider.getAddress().getPort());
        models = base.resolve("/v1beta/models/");
        groqEndpoint = base.resolve("/openai/v1/audio/transcriptions");
    }

    @AfterEach void stop() {
        if (stall != null) stall.countDown();
        provider.stop(0);
    }

    @Test void sendsInlineAudioWithGeminiMimeTypesAndReturnsMixedSpeech() {
        var transcriber = transcriber(true, "test-key", MODEL);
        assertTrue(transcriber.available());
        var mimeTypes = Map.of(FeedbackAudio.WEBM, "audio/webm", FeedbackAudio.OGG, "audio/ogg", FeedbackAudio.MP4, "audio/m4a");
        for (var format : FeedbackAudio.values()) {
            assertEquals(TRANSCRIPT, transcriber.transcribe(format, AUDIO, TIMEOUT));
            assertEquals("test-key", key);
            assertNull(query);
            var contents = request.path("contents");
            assertEquals(1, contents.size());
            var parts = contents.path(0).path("parts");
            assertEquals(1, parts.size());
            var inline = parts.path(0).path("inlineData");
            assertEquals(mimeTypes.get(format), inline.path("mimeType").asString());
            assertArrayEquals(AUDIO, Base64.getDecoder().decode(inline.path("data").asString()));
            String instructions = request.path("systemInstruction").path("parts").path(0).path("text").asString();
            assertTrue(instructions.contains("Sri Lankan Tamil") && instructions.contains("Never translate"));
            assertTrue(instructions.contains("English words and clauses actually spoken"));
            assertTrue(instructions.contains("class-ல") && instructions.contains("tuition-க்கு"));
            assertTrue(instructions.contains("வாறன்") && instructions.contains("வகுப்பு"));
            var config = request.path("generationConfig");
            assertEquals("application/json", config.path("responseMimeType").asString());
            assertEquals("transcript", config.path("responseJsonSchema").path("required").path(0).asString());
        }
        assertEquals(3, calls.get());
    }

    @Test void refusesDisabledMissingInvalidAndOversizedRequestsWithoutTalkingToProvider() {
        for (var unavailable : List.of(transcriber(false, "test-key", MODEL),
                transcriber(true, "", MODEL), transcriber(true, "test-key", "../bad"))) {
            assertFalse(unavailable.available());
            assertThrows(GeminiFeedbackTranscriber.Failed.class, () -> unavailable.transcribe(FeedbackAudio.OGG, AUDIO, TIMEOUT));
        }
        var transcriber = transcriber(true, "test-key", MODEL);
        assertThrows(GeminiFeedbackTranscriber.Failed.class, () -> transcriber.transcribe(FeedbackAudio.OGG, new byte[0], TIMEOUT));
        assertThrows(GeminiFeedbackTranscriber.Failed.class,
            () -> transcriber.transcribe(FeedbackAudio.OGG, new byte[FeedbackAudio.MAX_BYTES + 1], TIMEOUT));
        assertEquals(0, calls.get());
    }

    @Test void acceptsAnEmptyTranscriptForSilence() {
        response = transcript("  ");
        var transcriber = transcriber(true, "test-key", MODEL);
        assertEquals("", transcriber.transcribe(FeedbackAudio.WEBM, AUDIO, TIMEOUT));
    }

    @Test void rejectsIncompleteInvalidAndOversizedResponses() {
        var transcriber = transcriber(true, "test-key", MODEL);
        for (byte[] invalid : List.of("{}".getBytes(UTF_8), "{} {}".getBytes(UTF_8),
                candidate("{}", "SAFETY"), candidate("{}", "MAX_TOKENS"),
                candidate("{\"transcript\":3}", "STOP"), candidate("{\"transcript\":\"one\",\"transcript\":\"two\"}", "STOP"),
                candidate("{\"transcript\":\"text\",\"comment\":\"extra\"}", "STOP"),
                candidate("{\"transcript\":\"text\"} {}", "STOP"), transcript("```text```"),
                transcript("x".repeat(3_001)), "x".repeat(32_769).getBytes(UTF_8))) {
            response = invalid;
            var failure = assertThrows(GeminiFeedbackTranscriber.Failed.class,
                () -> transcriber.transcribe(FeedbackAudio.OGG, AUDIO, TIMEOUT));
            assertFalse(failure.temporary());
        }
        status = 429;
        response = "{\"error\":\"provider-private-message\"}".getBytes(UTF_8);
        var failure = assertThrows(GeminiFeedbackTranscriber.Failed.class,
            () -> transcriber.transcribe(FeedbackAudio.OGG, AUDIO, TIMEOUT));
        assertTrue(failure.temporary());
        assertNull(failure.getMessage());
        assertNull(failure.getCause());
    }

    @Test void timesOutWhenProviderBodyStalls() {
        stall = new CountDownLatch(1);
        var transcriber = transcriber(true, "test-key", MODEL);
        var failure = assertThrows(GeminiFeedbackTranscriber.Failed.class,
            () -> transcriber.transcribe(FeedbackAudio.WEBM, AUDIO, Duration.ofMillis(750)));
        assertTrue(failure.temporary());
        assertEquals(1, calls.get());
    }

    @Test void usesGeminiFirstAndFallsBackOnceOnQuotaOrProviderOutage() {
        var driver = new OfferFeedbackTranscriber(transcriber(true, "test-key", MODEL), groq(true, "groq-test-key"));
        assertTrue(driver.available());
        assertEquals(TRANSCRIPT, driver.transcribe(FeedbackAudio.OGG, AUDIO));
        assertEquals(0, groqCalls.get());
        for (int failure : List.of(408, 429, 500, 503, 504)) {
            status = failure;
            assertEquals(FALLBACK_TRANSCRIPT, driver.transcribe(FeedbackAudio.OGG, AUDIO));
        }
        assertEquals(6, calls.get());
        assertEquals(5, groqCalls.get());
        assertEquals("Bearer groq-test-key", groqKey);
        assertTrue(groqRequest.contains("filename=\"feedback.ogg\"") && groqRequest.contains("Content-Type: audio/ogg"));
        assertTrue(groqRequest.contains(new String(AUDIO, ISO_8859_1)));
        assertTrue(groqRequest.contains("whisper-large-v3") && groqRequest.contains("\r\n\r\nta\r\n"));
    }

    @Test void doesNotResendRejectedInvalidOrSilentAudioToGroq() {
        var driver = new OfferFeedbackTranscriber(transcriber(true, "test-key", MODEL), groq(true, "groq-test-key"));
        for (int rejection : List.of(400, 401, 403, 404, 413, 415, 422)) {
            status = rejection;
            assertThrows(OfferFeedbackTranscriber.Failed.class, () -> driver.transcribe(FeedbackAudio.OGG, AUDIO));
        }
        status = 200;
        response = candidate("{}", "SAFETY");
        assertThrows(OfferFeedbackTranscriber.Failed.class, () -> driver.transcribe(FeedbackAudio.OGG, AUDIO));
        response = "{}".getBytes(UTF_8);
        assertThrows(OfferFeedbackTranscriber.Failed.class, () -> driver.transcribe(FeedbackAudio.OGG, AUDIO));
        response = transcript("");
        assertEquals("", driver.transcribe(FeedbackAudio.OGG, AUDIO));
        assertEquals(0, groqCalls.get());
    }

    @Test void handlesAnUnconfiguredProviderAndStopsAfterOneFailedFallback() {
        var gemini = transcriber(true, "test-key", MODEL);
        var groq = groq(true, "groq-test-key");
        var groqOnly = new OfferFeedbackTranscriber(transcriber(true, "", MODEL), groq);
        assertTrue(groqOnly.available());
        assertEquals(FALLBACK_TRANSCRIPT, groqOnly.transcribe(FeedbackAudio.OGG, AUDIO));
        assertEquals(0, calls.get());
        assertFalse(new OfferFeedbackTranscriber(transcriber(false, "test-key", MODEL), groq(false, "groq-test-key")).available());

        status = 503;
        var geminiOnly = new OfferFeedbackTranscriber(gemini, groq(true, ""));
        assertThrows(OfferFeedbackTranscriber.Failed.class, () -> geminiOnly.transcribe(FeedbackAudio.OGG, AUDIO));
        assertEquals(1, groqCalls.get());

        groqStatus = 503;
        var both = new OfferFeedbackTranscriber(gemini, groq);
        assertThrows(OfferFeedbackTranscriber.Failed.class, () -> both.transcribe(FeedbackAudio.OGG, AUDIO));
        assertEquals(2, calls.get());
        assertEquals(2, groqCalls.get());
    }

    private GeminiFeedbackTranscriber transcriber(boolean enabled, String apiKey, String model) {
        return new GeminiFeedbackTranscriber(enabled, apiKey, model, models);
    }

    private GroqFeedbackTranscriber groq(boolean enabled, String apiKey) {
        return new GroqFeedbackTranscriber(enabled, apiKey, "whisper-large-v3", groqEndpoint);
    }


    private static byte[] transcript(String text) {
        return candidate(FeedbackProviderClient.JSON.writeValueAsString(Map.of("transcript", text)), "STOP");
    }

    private static byte[] candidate(String text, String reason) {
        return FeedbackProviderClient.JSON.writeValueAsBytes(Map.of("candidates", List.of(Map.of(
            "finishReason", reason, "content", Map.of("parts", List.of(Map.of("text", text)))))));
    }

    private void respond(HttpExchange exchange) throws IOException {
        try (exchange) {
            calls.incrementAndGet();
            key = exchange.getRequestHeaders().getFirst("x-goog-api-key");
            query = exchange.getRequestURI().getQuery();
            request = FeedbackProviderClient.JSON.readTree(exchange.getRequestBody().readAllBytes());
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] body = response;
            exchange.sendResponseHeaders(status, body.length);
            if (stall != null) stall.await(5, TimeUnit.SECONDS);
            exchange.getResponseBody().write(body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void respondGroq(HttpExchange exchange) throws IOException {
        try (exchange) {
            groqCalls.incrementAndGet();
            groqKey = exchange.getRequestHeaders().getFirst("Authorization");
            groqRequest = new String(exchange.getRequestBody().readAllBytes(), ISO_8859_1);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] body = FeedbackProviderClient.JSON.writeValueAsBytes(Map.of("text", FALLBACK_TRANSCRIPT));
            exchange.sendResponseHeaders(groqStatus, body.length);
            exchange.getResponseBody().write(body);
        }
    }
}
