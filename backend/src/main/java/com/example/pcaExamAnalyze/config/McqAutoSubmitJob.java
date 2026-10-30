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

    @Scheduled(initialDelay = 30_000, fixedDelay = 30_000)
    public void submitOverduePapers() {
        try {
            int count = 0;
            for (Long submissionId : studentExams.overdueSubmissionIds(100)) {
                try {
                    if (studentExams.autoSubmitOverdueById(submissionId)) count++;
                } catch (RuntimeException ex) {
                    // A conflict or corrupt single submission must not block other students.
                    log.warn("Could not auto-submit MCQ paper {}: {}", submissionId, ex.getMessage());
                }
            }
            if (count > 0) log.info("Auto-submitted {} overdue MCQ paper(s)", count);
        } catch (RuntimeException ex) {
            log.warn("MCQ auto-submit sweep failed: {}", ex.getMessage());
        }
    }
}
