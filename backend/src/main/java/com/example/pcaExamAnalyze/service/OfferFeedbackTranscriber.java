package com.example.pcaExamAnalyze.service;

import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class OfferFeedbackTranscriber {
    private static final long TOTAL_BUDGET = Duration.ofSeconds(25).toNanos();
    private static final Duration GEMINI_ONLY_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration GEMINI_PRIMARY_TIMEOUT = Duration.ofSeconds(14);
    private static final long GROQ_TIMEOUT = Duration.ofSeconds(10).toNanos();

    private final GeminiFeedbackTranscriber gemini;
    private final GroqFeedbackTranscriber groq;

    public OfferFeedbackTranscriber(GeminiFeedbackTranscriber gemini, GroqFeedbackTranscriber groq) {
        this.gemini = gemini;
        this.groq = groq;
    }

    public boolean available() {
        return gemini.available() || groq.available();
    }

    public String transcribe(FeedbackAudio format, byte[] audio) {
        boolean groqAvailable = groq.available();
        long deadline = System.nanoTime() + TOTAL_BUDGET;
        if (gemini.available()) {
            try {
                return gemini.transcribe(format, audio, groqAvailable ? GEMINI_PRIMARY_TIMEOUT : GEMINI_ONLY_TIMEOUT);
            } catch (GeminiFeedbackTranscriber.Failed e) {
                if (!e.temporary() || !groqAvailable) throw new Failed();
            }
        }
        long remaining = deadline - System.nanoTime();
        if (!groqAvailable || remaining <= 0) throw new Failed();
        try {
            return groq.transcribe(format, audio, Duration.ofNanos(Math.min(remaining, GROQ_TIMEOUT)));
        } catch (FeedbackProviderClient.Unavailable | IllegalArgumentException e) {
            throw new Failed();
        }
    }

    public static final class Failed extends RuntimeException {}
}
