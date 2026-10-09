package com.example.pcaExamAnalyze.report;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Builds exam reports in the background so a slow report (large exam, slow database, many images) never
 * runs into a browser or proxy timeout. The page starts a job, polls its progress and downloads the PDF when ready.
 */
@Service
public class ExamReportJobs {

    private static final Logger log = LoggerFactory.getLogger(ExamReportJobs.class);
    private static final long KEEP_MILLIS = 20 * 60 * 1000L;

    public enum State { RUNNING, DONE, FAILED }

    public static final class Job {
        final String id;
        final Long examId;
        final long created = System.currentTimeMillis();
        volatile State state = State.RUNNING;
        volatile String stage = "Starting";
        volatile String error = "";
        volatile byte[] bytes;
        volatile String fileName = "report.pdf";

        Job(String id, Long examId) { this.id = id; this.examId = examId; }

        public State state() { return state; }
        public String stage() { return stage; }
        public String error() { return error; }
        public byte[] bytes() { return bytes; }
        public String fileName() { return fileName; }
    }

    private final ExamReportPdfService pdf;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "exam-report");
        thread.setDaemon(true);
        return thread;
    });

    public ExamReportJobs(ExamReportPdfService pdf) {
        this.pdf = pdf;
    }

    /** Starts (or joins an already running) report job for the exam and returns its id. */
    public String start(Long examId) {
        purge();
        for (Job existing : jobs.values()) {
            if (existing.examId.equals(examId) && existing.state == State.RUNNING) return existing.id;
        }
        Job job = new Job(UUID.randomUUID().toString(), examId);
        jobs.put(job.id, job);
        pool.submit(() -> {
            try {
                ExamReportPdfService.Pdf result = pdf.render(examId, stage -> job.stage = stage);
                job.bytes = result.bytes();
                job.fileName = result.fileName();
                job.state = State.DONE;
                job.stage = "Ready";
            } catch (Throwable ex) {
                log.error("Exam report for exam {} failed", examId, ex);
                job.error = ex instanceof IllegalArgumentException ? ex.getMessage()
                        : "The report could not be created. Please try again; if it keeps failing, check the server log.";
                job.state = State.FAILED;
            }
        });
        return job.id;
    }

    public Job get(String id) {
        return id == null ? null : jobs.get(id);
    }

    private void purge() {
        long limit = System.currentTimeMillis() - KEEP_MILLIS;
        jobs.values().removeIf(job -> job.created < limit && job.state != State.RUNNING);
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }
}
