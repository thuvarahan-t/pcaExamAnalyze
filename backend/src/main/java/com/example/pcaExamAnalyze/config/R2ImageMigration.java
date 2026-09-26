package com.example.pcaExamAnalyze.config;

import com.example.pcaExamAnalyze.service.McqExamQuestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time move of question images that are still stored in the database to Cloudflare R2.
 * Runs at startup when R2 is configured; each image is moved in its own transaction, so a
 * failure leaves that image in the database (still served) and it is retried next start.
 */
@Component
public class R2ImageMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(R2ImageMigration.class);

    private final McqExamQuestionService questions;

    public R2ImageMigration(McqExamQuestionService questions) {
        this.questions = questions;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Long> ids = questions.idsWithDatabaseImages();
        if (ids.isEmpty()) return;
        int moved = 0;
        for (Long id : ids) {
            try {
                if (questions.moveImageToStorage(id)) moved++;
            } catch (RuntimeException ex) {
                log.warn("Could not move question image {} to R2: {}", id, ex.getMessage());
            }
        }
        log.info("Moved {} of {} question image(s) from the database to R2", moved, ids.size());
    }
}
