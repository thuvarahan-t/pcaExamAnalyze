package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.PcaExamAnalyzeApplication;
import com.example.pcaExamAnalyze.domain.OfferFeedback;
import com.example.pcaExamAnalyze.service.OfferContentService;
import com.example.pcaExamAnalyze.service.OfferContentService.FeedbackInput;
import com.example.pcaExamAnalyze.service.R2ImageStorage;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OfferFeedbackPhotoIntegrationTest {
    private static final R2ImageStorage STORAGE = mock(R2ImageStorage.class);
    private static ConfigurableApplicationContext context;
    private static OfferContentService content;
    private static byte[] fixture;

    @BeforeAll static void boot() throws Exception {
        context = new SpringApplicationBuilder(PcaExamAnalyzeApplication.class)
            .initializers(ctx -> ((GenericApplicationContext) ctx).registerBean("testFeedbackStorage",
                R2ImageStorage.class, () -> STORAGE, bean -> bean.setPrimary(true)))
            .run("--spring.profiles.active=dev", "--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:feedbackphotos;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "--pca.teacher.username=feedback-admin", "--pca.teacher.password=FeedbackTest123!",
                "--pca.storage.r2.account-id=", "--logging.level.root=ERROR", "--debug=false");
        content = context.getBean(OfferContentService.class);
        fixture = new ClassPathResource("package-posters/theory.jpg").getContentAsByteArray();
    }

    @AfterAll static void stop() {
        if (context != null) context.close();
    }

    @BeforeEach void resetStorage() {
        reset(STORAGE);
        when(STORAGE.enabled()).thenReturn(true);
    }

    @Test void storesCompressedBytesAndChecksModerationBeforeReadingR2() {
        String quote = "Photo attached to this pending feedback.";
        content.submitFeedback(input(quote), List.of(photo(fixture)));
        var entry = find(quote);
        assertNotNull(entry);
        assertEquals(OfferFeedback.Status.PENDING, entry.getStatus());
        assertTrue(entry.getPhotoKey().matches("offer-feedback/[a-f0-9-]{36}\\.jpg"));
        var bytes = ArgumentCaptor.forClass(byte[].class);
        verify(STORAGE).put(eq(entry.getPhotoKey()), bytes.capture(), eq("image/jpeg"));
        assertEquals(bytes.getValue().length, entry.getPhotoBytes());
        assertTrue(entry.getPhotoBytes() <= 100_000);
        assertTrue(entry.getPhotoWidth() > 0 && entry.getPhotoHeight() > 0);

        assertUnavailable(entry.getId(), HttpStatus.NOT_FOUND);
        verify(STORAGE, never()).read(anyString());
        when(STORAGE.read(entry.getPhotoKey())).thenReturn(bytes.getValue());
        assertArrayEquals(bytes.getValue(), content.feedbackPhoto(entry.getId(), true));
        content.moderate(entry.getId(), OfferFeedback.Status.PUBLISHED);
        assertArrayEquals(bytes.getValue(), content.feedbackPhoto(entry.getId(), false));
        content.moderate(entry.getId(), OfferFeedback.Status.HIDDEN);
        assertUnavailable(entry.getId(), HttpStatus.NOT_FOUND);
        verify(STORAGE, times(2)).read(entry.getPhotoKey());
        verify(STORAGE, never()).deleteQuietly(anyString());
    }

    @Test void rejectsInvalidFieldsAndAttachmentsBeforeWritingToR2() {
        assertThrows(IllegalArgumentException.class, () -> content.submitFeedback(input(""), List.of(photo(fixture))));
        assertThrows(IllegalArgumentException.class, () -> content.submitFeedback(input("Bad bytes"), List.of(photo(new byte[]{1}))));
        assertThrows(IllegalArgumentException.class,
            () -> content.submitFeedback(input("Two photos"), List.of(photo(fixture), photo(fixture))));
        verifyNoInteractions(STORAGE);
        assertNull(find("Bad bytes"));
        assertNull(find("Two photos"));
    }

    @Test void failedR2UploadKeepsTextOnlySubmissionAvailable() {
        String quote = "Text-only retry after a photo upload fails.";
        doThrow(new IllegalStateException("simulated storage failure")).when(STORAGE).put(anyString(), any(), anyString());
        var error = assertThrows(OfferContentService.FeedbackPhotoUnavailable.class,
            () -> content.submitFeedback(input(quote), List.of(photo(fixture))));
        assertTrue(error.getMessage().contains("remove the photo"));
        assertNull(find(quote));
        verify(STORAGE, timeout(5000)).deleteQuietly(startsWith("offer-feedback/"));
        content.submitFeedback(input(quote), null);
        var textOnly = find(quote);
        assertNotNull(textOnly);
        assertNull(textOnly.getPhotoKey());
        assertNull(textOnly.getPhotoBytes());
        assertEquals(OfferFeedback.Status.PENDING, textOnly.getStatus());
        assertUnavailable(textOnly.getId(), HttpStatus.NOT_FOUND);
        verify(STORAGE, never()).read(anyString());
    }

    @Test void rollbackAfterAnActualDatabaseInsertDeletesTheNewObject() {
        String quote = "Feedback deliberately rolled back.";
        var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        transaction.executeWithoutResult(status -> {
            content.submitFeedback(input(quote), List.of(photo(fixture)));
            assertNotNull(find(quote).getId());
            status.setRollbackOnly();
        });
        var key = ArgumentCaptor.forClass(String.class);
        verify(STORAGE).put(key.capture(), any(), eq("image/jpeg"));
        verify(STORAGE, timeout(5000)).deleteQuietly(key.getValue());
        assertNull(find(quote));
    }

    private static FeedbackInput input(String quote) {
        return new FeedbackInput("Feedback Student", "Test School", "Jaffna", "", quote);
    }

    private static MockMultipartFile photo(byte[] bytes) {
        return new MockMultipartFile("photo", "feedback.jpg", "image/jpeg", bytes);
    }

    private static OfferFeedback find(String quote) {
        return content.feedback(true).stream().filter(row -> row.getComment().equals(quote)).findFirst().orElse(null);
    }

    private static void assertUnavailable(Long id, HttpStatus status) {
        assertEquals(status, assertThrows(ResponseStatusException.class, () -> content.feedbackPhoto(id, false)).getStatusCode());
    }
}
