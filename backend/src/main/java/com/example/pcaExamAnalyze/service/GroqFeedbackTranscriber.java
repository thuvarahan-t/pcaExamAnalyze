package com.example.pcaExamAnalyze.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.util.regex.Pattern;

@Service
public class GroqFeedbackTranscriber {
    private static final URI TRANSCRIPTIONS = URI.create("https://api.groq.com/openai/v1/audio/transcriptions");
    private static final MediaType UTF8_TEXT = MediaType.parseMediaType("text/plain;charset=UTF-8");
    private static final String SRI_LANKAN_TAMIL_SPELLINGS =
        "இலங்கைத் தமிழ். tuition-க்கு, class-ல, notes, MCQ, Physics, PCA. "
        + "ரெண்டு நாள், ஒரு கிழமையா, போன கிழமை, வாறன், ஒண்டும், விளங்குது, படிப்பிச்சீங்க, எண்டா. இல்ல, class-க்கு.";
    private static final Pattern CLASS_ARRIVAL_MISSPELLING = Pattern.compile(
        "(?<![\\p{L}\\p{M}\\p{N}])கிழாசிக்கு(?=[,\\s]+(?:வாறன்|வாரன்)(?![\\p{L}\\p{M}\\p{N}]))");

    private final boolean enabled;
    private final String apiKey;
    private final String model;
    private final URI endpoint;

    @Autowired
    public GroqFeedbackTranscriber(@Value("${pca.feedback.voice.enabled:false}") boolean enabled,
                                  @Value("${pca.feedback.voice.groq.api-key:}") String apiKey,
                                  @Value("${pca.feedback.voice.groq.model:whisper-large-v3}") String model) {
        this(enabled, apiKey, model, TRANSCRIPTIONS);
    }

    GroqFeedbackTranscriber(boolean enabled, String apiKey, String model, URI endpoint) {
        this.enabled = enabled;
        this.apiKey = apiKey.strip();
        this.model = model.strip();
        this.endpoint = endpoint;
    }

    public boolean available() {
        return enabled && !apiKey.isEmpty() && !model.isEmpty();
    }

    String transcribe(FeedbackAudio format, byte[] audio, Duration timeout) {
        var parts = new MultipartBodyBuilder();
        parts.part("file", audio, format.mediaType()).filename("feedback." + format.mediaType().getSubtype());
        parts.part("model", model);
        parts.part("language", "ta");
        parts.part("prompt", SRI_LANKAN_TAMIL_SPELLINGS, UTF8_TEXT);
        parts.part("response_format", "json");

        var result = FeedbackProviderClient.json(FeedbackProviderClient.http(timeout).post().uri(endpoint)
            .headers(headers -> headers.setBearerAuth(apiKey))
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .accept(MediaType.APPLICATION_JSON)
            .body(parts.build()));
        String transcript = FeedbackProviderClient.plainText(result.path("text"));
        return CLASS_ARRIVAL_MISSPELLING.matcher(transcript).replaceAll("class-க்கு");
    }
}
