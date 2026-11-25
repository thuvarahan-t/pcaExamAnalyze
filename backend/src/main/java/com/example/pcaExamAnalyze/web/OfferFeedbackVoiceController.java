package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.service.FeedbackAudio;
import com.example.pcaExamAnalyze.service.GroqFeedbackTranscriber;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

@RestController
public class OfferFeedbackVoiceController {
    public static final String PATH = "/exam/packages/feedback/transcribe";
    private static final int MAX_PER_SESSION = 5;
    private static final int MAX_PER_MINUTE = 60;
    private static final long MINUTE = Duration.ofMinutes(1).toNanos();

    private final GroqFeedbackTranscriber groq;
    private final Semaphore activeRequests = new Semaphore(4);
    private final Map<String, Integer> requestsBySession = new HashMap<>();
    private long windowStarted = System.nanoTime();
    private int requestsInWindow;

    public OfferFeedbackVoiceController(GroqFeedbackTranscriber groq) {
        this.groq = groq;
    }

    @PostMapping(path = PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> transcribe(HttpServletRequest request) {
        if (!groq.available()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "Voice typing is unavailable. You can type your feedback below.");
        }
        if (request.getContentLengthLong() > FeedbackAudio.MAX_BYTES) return tooLarge();

        FeedbackAudio format;
        try {
            format = FeedbackAudio.forContentType(request.getContentType());
        } catch (InvalidMediaTypeException e) {
            format = null;
        }
        if (format == null) {
            return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "This recording format is unsupported. Try another browser or type your feedback.");
        }
        if (!activeRequests.tryAcquire()) return busy("5");

        try {
            byte[] audio = request.getInputStream().readNBytes(FeedbackAudio.MAX_BYTES + 1);
            if (audio.length > FeedbackAudio.MAX_BYTES) return tooLarge();
            if (!format.matches(audio)) {
                return error(HttpStatus.BAD_REQUEST, "The recording is empty or invalid. Try recording again.");
            }
            var session = request.getSession(false);
            if (!allow(session == null ? request.getRemoteAddr() : session.getId())) return busy("60");

            String text = groq.transcribe(format, audio);
            if (text.isBlank()) {
                return error(HttpStatus.UNPROCESSABLE_CONTENT, "No speech was recognized. Try recording again or type your feedback.");
            }
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Transcript(text));
        } catch (IOException e) {
            return error(HttpStatus.BAD_REQUEST, "Could not read the recording. Try recording again.");
        } catch (GroqFeedbackTranscriber.Failed e) {
            return error(HttpStatus.BAD_GATEWAY, "Could not transcribe the recording. Try again or type your feedback.");
        } finally {
            activeRequests.release();
        }
    }

    private synchronized boolean allow(String sessionKey) {
        long now = System.nanoTime();
        if (now - windowStarted >= MINUTE) {
            requestsBySession.clear();
            requestsInWindow = 0;
            windowStarted = now;
        }
        int count = requestsBySession.getOrDefault(sessionKey, 0);
        if (count >= MAX_PER_SESSION || requestsInWindow >= MAX_PER_MINUTE) return false;
        requestsBySession.put(sessionKey, count + 1);
        requestsInWindow++;
        return true;
    }

    private static ResponseEntity<Map<String, String>> tooLarge() {
        return error(HttpStatus.CONTENT_TOO_LARGE, "This recording is too large. Try a shorter recording.");
    }

    private static ResponseEntity<Map<String, String>> busy(String retryAfter) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.RETRY_AFTER, retryAfter)
            .body(Map.of("message", "Voice typing is busy. Wait a moment and try again, or type your feedback."));
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("message", message));
    }

    public record Transcript(String text) {
        @Override public String toString() { return "Transcript"; }
    }
}
