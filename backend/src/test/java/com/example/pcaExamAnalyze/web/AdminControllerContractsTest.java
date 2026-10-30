package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.domain.McqExamQuestion;
import com.example.pcaExamAnalyze.report.*;
import com.example.pcaExamAnalyze.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Admin controllers must report failures truthfully and retain their public HTTP contracts. */
class AdminControllerContractsTest {
    private McqAdminService service;
    private ExamReportPdfService reports;
    private ExamReportJobs jobs;
    private McqExamQuestionService images;
    private McqAdminController controller;

    @BeforeEach void setup() {
        service = mock(McqAdminService.class);
        reports = mock(ExamReportPdfService.class);
        jobs = mock(ExamReportJobs.class);
        images = mock(McqExamQuestionService.class);
        controller = new McqAdminController(service, images, mock(SyllabusService.class), reports, jobs);
    }

    @Test void sharedAdminModelContainsFiveOptionsAndActiveBatches() {
        when(service.activeBatchNames()).thenReturn(List.of("2027 A/L"));
        var model = new ExtendedModelMap();
        controller.commonModel(model);
        assertEquals(List.of("2027 A/L"), model.get("adminBatches"));
        assertEquals(5, ((List<?>) model.get("optionNumbers")).size());
        assertEquals(50, ((List<?>) model.get("questionNumbers")).size());
        assertNotNull(model.get("adminMonths"));
        assertNotNull(model.get("adminYears"));
    }

    @Test void batchRoutesShowSuccessAndValidationErrorsWithoutServerFailure() {
        var redirect = new RedirectAttributesModelMap();
        assertEquals("redirect:/exam/admin?popup=batches", controller.addBatch("New Batch", redirect));
        verify(service).addBatch("New Batch");
        assertEquals("redirect:/exam/admin?popup=batches", controller.toggleBatch(4L, redirect));
        assertEquals("redirect:/exam/admin?popup=batches", controller.renameBatch(4L, "Renamed", redirect));
        assertEquals("redirect:/exam/admin?popup=batches", controller.deleteBatch(4L, redirect));
        doThrow(new IllegalArgumentException("Already exists")).when(service).addBatch("Duplicate");
        var failed = new RedirectAttributesModelMap();
        assertEquals("redirect:/exam/admin?popup=batches", controller.addBatch("Duplicate", failed));
        assertEquals("Already exists", failed.getFlashAttributes().get("error"));
        doThrow(new IllegalArgumentException("Missing")).when(service).toggleBatch(404L);
        assertEquals("redirect:/exam/admin?popup=batches", controller.toggleBatch(404L, failed));
        doThrow(new IllegalArgumentException("Missing")).when(service).renameBatch(404L, "bad");
        assertEquals("redirect:/exam/admin?popup=batches", controller.renameBatch(404L, "bad", failed));
        doThrow(new IllegalArgumentException("Missing")).when(service).deleteBatch(404L);
        assertEquals("redirect:/exam/admin?popup=batches", controller.deleteBatch(404L, failed));
    }

    @Test void teacherReportReturnsPdfAndBusyRetryHeaderWhenCapacityIsExhausted() {
        var expected = new ExamReportPdfService.Pdf(new byte[]{37,80,68,70}, "analysis.pdf");
        when(reports.render(12L)).thenReturn(expected);
        var ok = controller.examReport(12L);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertArrayEquals(expected.bytes(), ok.getBody());
        assertTrue(ok.getHeaders().getContentDisposition().getFilename().contains("analysis"));
        when(reports.render(13L)).thenThrow(new RejectedExecutionException("busy"));
        var busy = controller.examReport(13L);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, busy.getStatusCode());
        assertEquals("30", busy.getHeaders().getFirst("Retry-After"));
        assertTrue(new String(busy.getBody(), StandardCharsets.UTF_8).contains("busy"));
    }

    @Test void reportStartGivesJobIdentifierOrRecoverableBusyResponse() {
        when(jobs.start(1L)).thenReturn("job123");
        var result = controller.startReport(1L);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("job123", result.getBody().get("job"));
        when(jobs.start(2L)).thenThrow(new RejectedExecutionException("all slots occupied"));
        var busy = controller.startReport(2L);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, busy.getStatusCode());
        assertEquals("30", busy.getHeaders().getFirst("Retry-After"));
    }

    @Test void expiredJobsReturnNotFoundInsteadOfClaimingPdfExists() {
        assertEquals(HttpStatus.NOT_FOUND, controller.reportStatus("missing").getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.reportFile("missing").getStatusCode());
        var job = mock(ExamReportJobs.Job.class);
        when(jobs.get("notready")).thenReturn(job);
        assertEquals(HttpStatus.NOT_FOUND, controller.reportFile("notready").getStatusCode());
    }

    @Test void releaseAndCloseRoutesDistinguishSuccessFromErrors() {
        var ok = new RedirectAttributesModelMap();
        assertEquals("redirect:/exam/admin/exams", controller.releaseResults(4L, ok));
        verify(service).releaseResults(4L);
        doThrow(new IllegalStateException("Incomplete key")).when(service).releaseResults(5L);
        var bad = new RedirectAttributesModelMap();
        assertEquals("redirect:/exam/admin/exams", controller.releaseResults(5L, bad));
        assertEquals("Incomplete key", bad.getFlashAttributes().get("error"));

        assertEquals("redirect:/exam/admin/exams", controller.closeExam(4L, ok));
        assertEquals("redirect:/exam/admin/exams", controller.openExam(4L, ok));
        verify(service).setExamClosed(4L, true);
        verify(service).setExamClosed(4L, false);
        doThrow(new IllegalStateException("Draft")).when(service).setExamClosed(5L, true);
        assertEquals("redirect:/exam/admin/exams", controller.closeExam(5L, bad));
        doThrow(new IllegalStateException("Draft")).when(service).setExamClosed(5L, false);
        assertEquals("redirect:/exam/admin/exams", controller.openExam(5L, bad));
    }

    @Test void adminCsvDownloadContainsAttachmentAndExactPayload() {
        when(service.exportCsv(12L)).thenReturn("A,B\n1,2".getBytes(StandardCharsets.UTF_8));
        var result = controller.exportExam(12L);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("A,B\n1,2", new String(result.getBody(), StandardCharsets.UTF_8));
        assertEquals("text/csv", result.getHeaders().getContentType().getType() + "/" + result.getHeaders().getContentType().getSubtype());
        assertTrue(result.getHeaders().getContentDisposition().getFilename().contains("12"));
        assertEquals(HttpStatus.OK, controller.exportAll(12L).getStatusCode());
    }

    @Test void questionImageReturnsNotFoundOrPrivateInlineImage() {
        assertEquals(HttpStatus.NOT_FOUND, controller.questionImage(1L, 1).getStatusCode());
        var image = new McqExamQuestion();
        image.setContentType("image/png");
        image.setData(new byte[]{4,5,6});
        when(service.questionImage(1L, 2)).thenReturn(Optional.of(image));
        var response = controller.questionImage(1L, 2);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(new byte[]{4,5,6}, response.getBody());
    }

    @Test void archivingAndDeletingExamRoutesRetainFlashMessageAndRedirect() {
        var redirect = new RedirectAttributesModelMap();
        assertEquals("redirect:/exam/admin/exams", controller.archive(10L, redirect));
        assertEquals("redirect:/exam/admin/exams", controller.deleteExam(11L, redirect));
        assertEquals("redirect:/exam/admin/exams", controller.unpublishResults(12L, redirect));
        assertEquals("redirect:/exam/admin/submissions", controller.deleteSubmission(13L, redirect));
        verify(service).archive(10L);
        verify(service).deleteExam(11L);
        verify(service).unpublishResults(12L);
        verify(service).deleteSubmission(13L);
    }
}
