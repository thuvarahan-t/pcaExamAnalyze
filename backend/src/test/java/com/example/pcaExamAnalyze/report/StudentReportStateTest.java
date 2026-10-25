package com.example.pcaExamAnalyze.report;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StudentReportStateTest {
    @Test void answerChartKeepsFreeMarksSeparateAndHandlesEmptyExams() {
        var rows = StudentReportCharts.slices(2, 1, 0, 1);
        assertEquals(4, rows.stream().mapToInt(StudentReportCharts.Slice::count).sum());
        assertEquals("50.0%", rows.getFirst().percentage());
        assertEquals("Free marks", rows.getLast().label());
        assertEquals("25.0%", rows.getLast().percentage());
        assertTrue(StudentReportCharts.slices(0, 0, 0, 0).stream()
                .allMatch(row -> row.percentage().equals("0.0%")));
    }

    @Test void jobProgressCannotFinishBeforePdfExistsOrMoveBackwards() {
        var job = new StudentReportJobs.Job("session", List.of(4L, 5L));
        job.progress("Rendering", 70);
        job.progress("Delayed callback", 20);
        assertEquals(70, job.status().get("percentage"));
        job.progress("Finishing", 100);
        assertEquals(99, job.status().get("percentage"));
        assertNull(job.pdf());
        var pdf = new ExamReportPdfService.Pdf(new byte[]{1, 2}, "report.pdf");
        job.done(pdf);
        assertEquals(100, job.status().get("percentage"));
        assertEquals("DONE", job.status().get("state"));
        assertSame(pdf, job.pdf());
        assertFalse(job.running());
    }
}
