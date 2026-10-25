package com.example.pcaExamAnalyze.report;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Caps how many PDFs are built at once across every entry point (background jobs and the plain download links).
 * Each build holds decoded question images, embedded data URIs and the PDF in memory, so unbounded parallel builds
 * multiply the heap needed. Callers wait for a slot up to a limit, then get "busy" instead of queueing forever.
 * Acquire it outside any database transaction so a waiting request never holds a pooled connection.
 */
@Component
public class PdfRenderGate {

    private final Semaphore slots;
    private final long waitSeconds;

    public PdfRenderGate(@Value("${pca.pdf.max-concurrent:2}") int maxConcurrent,
                         @Value("${pca.pdf.wait-seconds:120}") long waitSeconds) {
        this.slots = new Semaphore(Math.max(1, maxConcurrent), true);
        this.waitSeconds = Math.max(0, waitSeconds);
    }

    public <T> T run(Supplier<T> work) {
        try {
            if (!slots.tryAcquire(waitSeconds, TimeUnit.SECONDS)) {
                throw new RejectedExecutionException("Reports are busy. Please try again shortly.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException("Interrupted while waiting for a report slot", ex);
        }
        try {
            return work.get();
        } finally {
            slots.release();
        }
    }
}
