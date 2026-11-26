package com.example.pcaExamAnalyze.service;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.regex.Pattern;

final class FeedbackProviderClient {
    static final int MAX_TEXT_LENGTH = 3_000;
    private static final int MAX_RESPONSE_BYTES = 32_768;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    static final JsonMapper JSON = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private FeedbackProviderClient() {}

    static RestClient http(Duration responseTimeout) {
        var factory = new JdkClientHttpRequestFactory(HTTP);
        factory.setReadTimeout(responseTimeout);
        return RestClient.builder().requestFactory(factory).build();
    }

    static JsonNode json(RestClient.RequestHeadersSpec<?> request) {
        try {
            return request.exchange((sent, response) -> {
                var status = response.getStatusCode();
                if (!status.is2xxSuccessful()) {
                    throw new Unavailable(status.value() == 408 || status.value() == 429 || status.is5xxServerError());
                }
                byte[] body = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                if (body.length > MAX_RESPONSE_BYTES) throw new Unavailable();
                var json = JSON.readTree(body);
                if (json == null || !json.isObject()) throw new Unavailable();
                return json;
            });
        } catch (ResourceAccessException e) {
            throw new Unavailable(!Thread.currentThread().isInterrupted());
        } catch (RestClientException | JacksonException e) {
            // Neither credentials nor provider bodies should appear in app errors or logs.
            throw new Unavailable();
        }
    }

    static String plainText(JsonNode node) {
        if (!node.isString()) throw new Unavailable();
        String text = WHITESPACE.matcher(node.asString()).replaceAll(" ").strip();
        if (text.length() > MAX_TEXT_LENGTH || text.codePoints().anyMatch(FeedbackProviderClient::invalidCharacter)) {
            throw new Unavailable();
        }
        return text;
    }

    private static boolean invalidCharacter(int value) {
        int type = Character.getType(value);
        // Some Tamil text uses join controls, which are part of its spelling.
        return Character.isISOControl(value) || type == Character.SURROGATE
            || (type == Character.FORMAT && value != '\u200c' && value != '\u200d');
    }

    static final class Unavailable extends RuntimeException {
        private final boolean temporary;

        Unavailable() { this(false); }

        Unavailable(boolean temporary) { this.temporary = temporary; }

        boolean temporary() { return temporary; }
    }
}
