package com.example.pcaExamAnalyze.service;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.regex.Pattern;

final class FeedbackProviderClient {
    static final int MAX_TEXT_LENGTH = 3_000;
    private static final int MAX_RESPONSE_BYTES = 32_768;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final JsonMapper JSON = JsonMapper.shared();

    private FeedbackProviderClient() {}

    static RestClient http(Duration responseTimeout) {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(responseTimeout);
        return RestClient.builder().requestFactory(factory).build();
    }

    static JsonNode json(RestClient.RequestHeadersSpec<?> request) {
        try {
            return request.exchange((sent, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) throw new Unavailable();
                byte[] body = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                if (body.length > MAX_RESPONSE_BYTES) throw new Unavailable();
                var json = JSON.readTree(body);
                if (json == null || !json.isObject()) throw new Unavailable();
                return json;
            });
        } catch (RestClientException | JacksonException e) {
            // Neither credentials nor provider bodies should appear in app errors or logs.
            throw new Unavailable();
        }
    }

    static String plainText(JsonNode node) {
        if (!node.isString()) throw new Unavailable();
        String text = WHITESPACE.matcher(node.asString()).replaceAll(" ").strip();
        if (text.length() > MAX_TEXT_LENGTH || text.codePoints().anyMatch(Character::isISOControl)) {
            throw new Unavailable();
        }
        return text;
    }

    static final class Unavailable extends RuntimeException {}
}
