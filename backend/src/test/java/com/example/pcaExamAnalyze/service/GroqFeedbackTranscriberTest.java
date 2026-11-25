package com.example.pcaExamAnalyze.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GroqFeedbackTranscriberTest {
    private HttpServer provider;
    private URI endpoint;
    private volatile String requestBody;
    private volatile String authorization;
    private volatile String requestContentType;
    private volatile int status = 200;
    private volatile String response = "{\"text\":\"  எனக்கு   PCA பிடிக்கும். \\n நன்றி! \"}";
    private volatile CountDownLatch waitForTimeout;

    @BeforeEach void start() throws IOException {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/openai/v1/audio/transcriptions", this::respond);
        provider.start();
        endpoint = URI.create("http://127.0.0.1:" + provider.getAddress().getPort() + "/openai/v1/audio/transcriptions");
    }

    @AfterEach void stop() {
        if (waitForTimeout != null) waitForTimeout.countDown();
        provider.stop(0);
    }

    @Test void sendsTamilHintAndAudioAndFormatsText() {
        var transcriber = transcriber(true, "test-key", Duration.ofSeconds(3));
        assertEquals("எனக்கு PCA பிடிக்கும். நன்றி!", transcriber.transcribe(FeedbackAudio.OGG, FeedbackAudioTest.oggOpus()));
        assertEquals("Bearer test-key", authorization);
        assertTrue(requestContentType.startsWith("multipart/form-data;"));
        assertTrue(requestBody.contains("name=\"file\"; filename=\"feedback.ogg\""));
        assertTrue(requestBody.contains("Content-Type: audio/ogg"));
        assertTrue(requestBody.contains("OpusHead"));
        assertTrue(requestBody.contains("name=\"model\"") && requestBody.contains("whisper-large-v3"));
        assertTrue(requestBody.contains("name=\"language\"") && requestBody.contains("\r\n\r\nta\r\n"));
        assertTrue(requestBody.contains("name=\"response_format\"") && requestBody.contains("\r\n\r\njson\r\n"));
    }

    @Test void disabledOrMissingKeyNeverCallsGroq() {
        assertFalse(transcriber(false, "test-key", Duration.ofSeconds(3)).available());
        var transcriber = transcriber(true, "", Duration.ofSeconds(3));
        assertFalse(transcriber.available());
        assertThrows(GroqFeedbackTranscriber.Failed.class, () -> transcriber.transcribe(FeedbackAudio.OGG, FeedbackAudioTest.oggOpus()));
        assertNull(requestBody);
    }

    @Test void masksProviderFailureAndRejectsInvalidResponses() {
        var transcriber = transcriber(true, "test-key", Duration.ofSeconds(3));
        status = 429;
        var error = assertThrows(GroqFeedbackTranscriber.Failed.class,
            () -> transcriber.transcribe(FeedbackAudio.OGG, FeedbackAudioTest.oggOpus()));
        assertNull(error.getMessage());
        assertNull(error.getCause());

        status = 200;
        for (String invalid : new String[] { "{", "{}", "{\"text\":3}", "{\"text\":\"x" + "x".repeat(3_000) + "\"}", "x".repeat(32_769) }) {
            response = invalid;
            assertThrows(GroqFeedbackTranscriber.Failed.class,
                () -> transcriber.transcribe(FeedbackAudio.OGG, FeedbackAudioTest.oggOpus()));
        }
    }

    @Test void timesOutAfterResponseHeadersWhenTheBodyStalls() {
        waitForTimeout = new CountDownLatch(1);
        var transcriber = transcriber(true, "test-key", Duration.ofMillis(300));
        assertThrows(GroqFeedbackTranscriber.Failed.class, () -> transcriber.transcribe(FeedbackAudio.OGG, FeedbackAudioTest.oggOpus()));
        assertNotNull(requestBody);
    }

    private GroqFeedbackTranscriber transcriber(boolean enabled, String key, Duration timeout) {
        return new GroqFeedbackTranscriber(enabled, key, "whisper-large-v3", FeedbackProviderClient.http(timeout), endpoint);
    }

    private void respond(HttpExchange exchange) throws IOException {
        try (exchange) {
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            requestContentType = exchange.getRequestHeaders().getFirst("Content-Type");
            requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            if (waitForTimeout != null) waitForTimeout.await(5, TimeUnit.SECONDS);
            exchange.getResponseBody().write(bytes);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
