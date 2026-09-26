package com.example.pcaExamAnalyze.config;

import com.example.pcaExamAnalyze.service.McqStudentExamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Submits timed MCQ papers whose time has run out, even if the student's browser is closed. */
@Component
@EnableScheduling
public class McqAutoSubmitJob {

    private static final Logger log = LoggerFactory.getLogger(McqAutoSubmitJob.class);

    private final McqStudentExamService studentExams;

    public McqAutoSubmitJob(McqStudentExamService studentExams) {
        this.studentExams = studentExams;
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void submitOverduePapers() {
        try {
            int count = studentExams.autoSubmitOverdue();
            if (count > 0) log.info("Auto-submitted {} MCQ paper(s) whose time ran out", count);
        } catch (RuntimeException ex) {
            log.warn("MCQ auto-submit sweep failed: {}", ex.getMessage());
        }
    }
}
