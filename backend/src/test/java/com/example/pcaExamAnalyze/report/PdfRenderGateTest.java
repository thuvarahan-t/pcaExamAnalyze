package com.example.pcaExamAnalyze.report;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PdfRenderGateTest {

    @Test void busyWhenEverySlotIsTakenAndFreeAgainAfterwards() throws Exception {
        var gate = new PdfRenderGate(1, 0);
        var holding = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var worker = new Thread(() -> gate.run(() -> {
            holding.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            return null;
        }));
        worker.start();
        assertTrue(holding.await(5, TimeUnit.SECONDS));
        assertThrows(RejectedExecutionException.class, () -> gate.run(() -> "second"));
        release.countDown();
        worker.join(5000);
        assertEquals("next", gate.run(() -> "next"));
    }

    @Test void slotIsReleasedWhenRenderingFails() {
        var gate = new PdfRenderGate(1, 0);
        assertThrows(IllegalStateException.class, () -> gate.run(() -> { throw new IllegalStateException("boom"); }));
        assertEquals("ok", gate.run(() -> "ok"));
    }
}
