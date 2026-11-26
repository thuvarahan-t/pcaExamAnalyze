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
import static org.junit.jupiter.api.Assertions.*;

class GeminiFeedbackTranscriberTest {
    private static final String MODEL = "gemini-3.5-flash-lite";
    private static final String TRANSCRIPT = "இண்டைக்கு tuition-க்கு வாறன். The class was good.";
    private static final byte[] AUDIO = {1, 2, -1, 0, 127};
    private HttpServer provider;
    private URI models;
    private volatile byte[] response = transcript(TRANSCRIPT);
    private volatile int status = 200;
    private volatile JsonNode request;
    private volatile String key;
    private volatile String query;
    private volatile CountDownLatch stall;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach void start() throws IOException {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/v1beta/models/" + MODEL + ":generateContent", this::respond);
        provider.start();
        models = URI.create("http://127.0.0.1:" + provider.getAddress().getPort() + "/v1beta/models/");
    }

    @AfterEach void stop() {
        if (stall != null) stall.countDown();
        provider.stop(0);
    }

    @Test void sendsInlineAudioWithGeminiMimeTypesAndReturnsMixedSpeech() {
        var transcriber = transcriber(true, "test-key", MODEL, Duration.ofSeconds(3));
        assertTrue(transcriber.available());
        var mimeTypes = Map.of(FeedbackAudio.WEBM, "audio/webm", FeedbackAudio.OGG, "audio/ogg", FeedbackAudio.MP4, "audio/m4a");
        for (var format : FeedbackAudio.values()) {
            assertEquals(TRANSCRIPT, transcriber.transcribe(format, AUDIO));
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
        for (var unavailable : List.of(transcriber(false, "test-key", MODEL, Duration.ofSeconds(3)),
                transcriber(true, "", MODEL, Duration.ofSeconds(3)), transcriber(true, "test-key", "../bad", Duration.ofSeconds(3)))) {
            assertFalse(unavailable.available());
            assertThrows(GeminiFeedbackTranscriber.Failed.class, () -> unavailable.transcribe(FeedbackAudio.OGG, AUDIO));
        }
        var transcriber = transcriber(true, "test-key", MODEL, Duration.ofSeconds(3));
        assertThrows(GeminiFeedbackTranscriber.Failed.class, () -> transcriber.transcribe(FeedbackAudio.OGG, new byte[0]));
        assertThrows(GeminiFeedbackTranscriber.Failed.class,
            () -> transcriber.transcribe(FeedbackAudio.OGG, new byte[FeedbackAudio.MAX_BYTES + 1]));
        assertEquals(0, calls.get());
    }

    @Test void acceptsAnEmptyTranscriptForSilence() {
        response = transcript("  ");
        var transcriber = transcriber(true, "test-key", MODEL, Duration.ofSeconds(3));
        assertEquals("", transcriber.transcribe(FeedbackAudio.WEBM, AUDIO));
    }

    @Test void rejectsIncompleteInvalidAndOversizedResponses() {
        var transcriber = transcriber(true, "test-key", MODEL, Duration.ofSeconds(3));
        for (byte[] invalid : List.of("{}".getBytes(UTF_8), "{} {}".getBytes(UTF_8),
                candidate("{}", "SAFETY"), candidate("{}", "MAX_TOKENS"),
                candidate("{\"transcript\":3}", "STOP"), candidate("{\"transcript\":\"one\",\"transcript\":\"two\"}", "STOP"),
                candidate("{\"transcript\":\"text\",\"comment\":\"extra\"}", "STOP"),
                candidate("{\"transcript\":\"text\"} {}", "STOP"), transcript("```text```"),
                transcript("x".repeat(3_001)), "x".repeat(32_769).getBytes(UTF_8))) {
            response = invalid;
            assertThrows(GeminiFeedbackTranscriber.Failed.class, () -> transcriber.transcribe(FeedbackAudio.OGG, AUDIO));
        }
        status = 429;
        response = "{\"error\":\"provider-private-message\"}".getBytes(UTF_8);
        var failure = assertThrows(GeminiFeedbackTranscriber.Failed.class, () -> transcriber.transcribe(FeedbackAudio.OGG, AUDIO));
        assertNull(failure.getMessage());
        assertNull(failure.getCause());
    }

    @Test void timesOutWhenProviderBodyStalls() {
        stall = new CountDownLatch(1);
        var transcriber = transcriber(true, "test-key", MODEL, Duration.ofMillis(750));
        assertThrows(GeminiFeedbackTranscriber.Failed.class, () -> transcriber.transcribe(FeedbackAudio.WEBM, AUDIO));
        assertEquals(1, calls.get());
    }

    private GeminiFeedbackTranscriber transcriber(boolean enabled, String apiKey, String model, Duration timeout) {
        return new GeminiFeedbackTranscriber(enabled, apiKey, model, FeedbackProviderClient.http(timeout), models);
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
}
