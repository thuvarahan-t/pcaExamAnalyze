package com.example.pcaExamAnalyze.report;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/** Student PDF jobs are private to the browser session that requested them. */
@Service
public class StudentReportJobs {
    public static final class Job {
        public final String id = UUID.randomUUID().toString();
        public final Long submissionId;
        public final java.util.List<Long> submissionIds;
        private final String owner;
        private volatile long touched = System.currentTimeMillis();
        private String state = "RUNNING", stage = "Waiting to prepare your report", error = "";
        private int percentage = 1;
        private ExamReportPdfService.Pdf pdf;
        Job(String owner, Long submissionId) { this.owner = owner; this.submissionId = submissionId; this.submissionIds = java.util.List.of(submissionId); }
        Job(String owner, java.util.List<Long> ids) { this.owner = owner; this.submissionId = -1L; this.submissionIds = java.util.List.copyOf(ids); }
        synchronized void progress(String stage, int percentage) {
            this.stage = stage;
            this.percentage = Math.max(this.percentage, Math.min(99, percentage));
        }
        public synchronized Map<String, Object> status() {
            return Map.of("state", state, "stage", stage, "percentage", percentage, "error", error);
        }
        public synchronized ExamReportPdfService.Pdf pdf() { return pdf; }
        synchronized boolean running() { return "RUNNING".equals(state); }
        synchronized void done(ExamReportPdfService.Pdf pdf) {
            this.pdf = pdf; state = "DONE"; stage = "Your report is ready"; percentage = 100;
            touched = System.currentTimeMillis();
        }
        synchronized void failed() {
            state = "FAILED"; error = "Your report could not be prepared. Please try again.";
            touched = System.currentTimeMillis();
        }
    }
    private final StudentReportPdfService reports;
    private final StudentPerformanceReportService performance;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), task -> {
                Thread thread = new Thread(task, "student-pdf"); thread.setDaemon(true); return thread;
            });
    public StudentReportJobs(StudentReportPdfService reports, StudentPerformanceReportService performance) {
        this.reports = reports; this.performance = performance;
    }
    public synchronized String startAnalysis(String owner, java.util.List<Long> ids, String registration, String nic) {
        purge();
        var selection = ids.stream().distinct().sorted().toList();
        for (Job job : jobs.values())
            if (job.owner.equals(owner) && job.submissionId == -1L && job.submissionIds.equals(selection) && job.running()) return job.id;
        if (jobs.size() >= 40) throw new RejectedExecutionException("Reports are busy. Please try again shortly.");
        Job job = new Job(owner, selection); jobs.put(job.id, job);
        try {
            pool.execute(() -> {
                try { job.done(performance.render(selection, registration, nic, job::progress)); }
                catch (Exception ex) {
                    LoggerFactory.getLogger(StudentReportJobs.class).error("Student performance PDF failed", ex);
                    job.failed();
                }
            });
        } catch (RejectedExecutionException ex) { jobs.remove(job.id); throw ex; }
        return job.id;
    }
    public synchronized String start(String owner, Long id, String registration, String nic) {
        purge();
        for (Job job : jobs.values())
            if (job.owner.equals(owner) && job.submissionId.equals(id) && job.running()) return job.id;
        if (jobs.size() >= 40) throw new RejectedExecutionException("Reports are busy. Please try again shortly.");
        Job job = new Job(owner, id);
        jobs.put(job.id, job);
        try {
            pool.execute(() -> {
                try { job.done(reports.render(id, registration, nic, job::progress)); }
                catch (Exception ex) {
                    LoggerFactory.getLogger(StudentReportJobs.class).error("Student PDF {} failed", id, ex);
                    job.failed();
                }
            });
        } catch (RejectedExecutionException ex) { jobs.remove(job.id); throw ex; }
        return job.id;
    }
    public Job get(String id, String owner) {
        purge();
        Job job = jobs.get(id);
        if (job == null || !job.owner.equals(owner)) return null;
        job.touched = System.currentTimeMillis();
        return job;
    }
    private void purge() {
        long limit = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10);
        jobs.values().removeIf(job -> !job.running() && job.touched < limit);
    }
    @PreDestroy void close() { pool.shutdownNow(); }
}
