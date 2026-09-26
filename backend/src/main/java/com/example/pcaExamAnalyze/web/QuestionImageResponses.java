package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.domain.McqExamQuestion;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.time.Duration;

/** HTTP response for a question image: a redirect to R2, or the bytes kept in the database. */
final class QuestionImageResponses {

    private QuestionImageResponses() {}

    static ResponseEntity<byte[]> of(com.example.pcaExamAnalyze.domain.McqSubImage image, String redirectUrl) {
        if (redirectUrl != null) return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(20)).cachePrivate()).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate()).body(image.getData());
    }

    /**
     * @param redirectUrl presigned R2 link, or null when the image is stored in the database.
     *                    The redirect is cached briefly (well inside the link's 30 min validity)
     *                    so moving between questions does not re-sign every time.
     */
    static ResponseEntity<byte[]> of(McqExamQuestion image, String redirectUrl) {
        if (redirectUrl != null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(redirectUrl))
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(20)).cachePrivate())
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .body(image.getData());
    }
}
