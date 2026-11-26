package com.example.pcaExamAnalyze.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

@Service
public class GroqFeedbackTranscriber {
    private static final URI TRANSCRIPTIONS = URI.create("https://api.groq.com/openai/v1/audio/transcriptions");
    private static final MediaType UTF8_TEXT = MediaType.parseMediaType("text/plain;charset=UTF-8");
    // Short spelling hints give English terms and Tamil suffixes without providing a sentence to copy.
    private static final String SRI_LANKAN_TAMIL_SPELLINGS =
        "இலங்கைத் தமிழ். tuition-க்கு, class-ல, notes, MCQ, Physics, PCA. "
        + "ரெண்டு நாள், ஒரு கிழமையா, போன கிழமை, வாறன், ஒண்டும், விளங்குது, படிப்பிச்சீங்க, எண்டா.";

    private final boolean enabled;
    private final String apiKey;
    private final String model;
    private final RestClient client;
    private final URI endpoint;

    @Autowired
    public GroqFeedbackTranscriber(@Value("${pca.feedback.voice.enabled}") boolean enabled,
                                  @Value("${pca.feedback.voice.groq.api-key}") String apiKey,
                                  @Value("${pca.feedback.voice.groq.model}") String model) {
        this(enabled, apiKey, model, FeedbackProviderClient.http(Duration.ofSeconds(12)), TRANSCRIPTIONS);
    }

    GroqFeedbackTranscriber(boolean enabled, String apiKey, String model, RestClient client, URI endpoint) {
        this.enabled = enabled;
        this.apiKey = apiKey.strip();
        this.model = model.strip();
        this.client = client;
        this.endpoint = endpoint;
    }

    public boolean available() {
        return enabled && !apiKey.isEmpty() && !model.isEmpty();
    }

    public String transcribe(FeedbackAudio format, byte[] audio) throws Failed {
        if (!available()) throw new Failed();
        var parts = new MultipartBodyBuilder();
        parts.part("file", audio, format.mediaType()).filename(format.fileName());
        parts.part("model", model);
        parts.part("language", "ta");
        parts.part("prompt", SRI_LANKAN_TAMIL_SPELLINGS, UTF8_TEXT);
        parts.part("response_format", "json");

        try {
            var result = FeedbackProviderClient.json(client.post().uri(endpoint)
                .headers(headers -> headers.setBearerAuth(apiKey))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.APPLICATION_JSON)
                .body(parts.build()));
            return FeedbackProviderClient.plainText(result.path("text"));
        } catch (FeedbackProviderClient.Unavailable e) {
            throw new Failed();
        }
    }

    public static final class Failed extends RuntimeException {}
}
