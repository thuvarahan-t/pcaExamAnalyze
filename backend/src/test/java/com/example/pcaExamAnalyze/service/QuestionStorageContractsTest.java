package com.example.pcaExamAnalyze.service;

import com.example.pcaExamAnalyze.domain.*;
import com.example.pcaExamAnalyze.repo.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Isolated image validation, metadata and R2 contract tests; does not make network requests. */
class QuestionStorageContractsTest {
    private final McqExamQuestionRepository questions = mock(McqExamQuestionRepository.class);
    private final R2ImageStorage storage = mock(R2ImageStorage.class);
    private final McqQuestionTagRepository tags = mock(McqQuestionTagRepository.class);
    private final SyllabusService syllabus = mock(SyllabusService.class);
    private final McqSubImageRepository subs = mock(McqSubImageRepository.class);
    private final McqExamQuestionService service = new McqExamQuestionService(questions, storage, tags, syllabus, subs);

    private McqExam exam() {
        var exam = new McqExam();
        exam.setId(5L);
        exam.setTotalQuestions(4);
        return exam;
    }
    private MockMultipartFile png() { return new MockMultipartFile("image", "question.png", "image/png", new byte[]{1,2,3}); }
    private McqExamQuestionService.QuestionEdit edit() {
        return new McqExamQuestionService.QuestionEdit(3.3, 60,
                List.of("Mechanics"), List.of("Motion"), List.of("Graphs"),
                List.of("Gradient"), null);
    }

    @Test void uploadValidationRejectsUnsupportedFormatsAndOversizedImages() {
        assertNull(McqExamQuestionService.problem(null));
        assertNull(McqExamQuestionService.problem(new MockMultipartFile("image", new byte[0])));
        assertNull(McqExamQuestionService.problem(png()));
        assertEquals("Upload a PNG, JPG, WEBP or GIF image",
                McqExamQuestionService.problem(new MockMultipartFile("image", "a.svg", "image/svg+xml", new byte[]{1})));
        assertEquals("Image must be 5 MB or smaller",
                McqExamQuestionService.problem(new MockMultipartFile("image", "huge.png", "image/png",
                        new byte[McqExamQuestion.MAX_IMAGE_BYTES + 1])));
        assertNull(McqExamQuestionService.problem(new MockMultipartFile("image", "a.JPG", "IMAGE/JPEG", new byte[]{1})));
    }

    @Test void missingImagesAndQuestionMetadataDoNotLoadImageBytes() {
        when(questions.findMetaByExamId(5L)).thenReturn(List.of(
                new McqQuestionMeta(1, Instant.now(), 3.0, 60, "Mechanics", null, null, null, null),
                new McqQuestionMeta(3, null, null, null, null, null, null, null, null)));
        assertEquals(List.of(2,3,4), service.missingImages(5L, 4));
        assertEquals(List.of(1,2), service.missingImages(null, 2));
        assertEquals("Mechanics", service.meta(5L).get(1).unitList().getFirst());
        assertFalse(service.meta(5L).get(3).hasImage());
        var image = new McqExamQuestion();
        when(questions.findByExamIdAndQuestionNumber(5L, 2)).thenReturn(Optional.of(image));
        assertTrue(service.findImage(5L, 2).isEmpty());
        image.setData(new byte[]{1});
        assertTrue(service.findImage(5L, 2).isPresent());
    }

    @Test void directImageLinksAreDisabledWhenR2IsNotConfigured() {
        assertTrue(service.directLinks(5L, Duration.ofMinutes(10)).main().isEmpty());
        assertTrue(service.links(Map.of(), List.of(), Duration.ofMinutes(10)).sub().isEmpty());
        assertTrue(service.imageBytesByKey("key").isEmpty());
        assertTrue(service.imageBytesByKey(null).isEmpty());
        assertTrue(service.idsWithDatabaseImages().isEmpty());
        assertFalse(service.moveImageToStorage(10L));
        assertNull(service.imageRedirectUrl(new McqExamQuestion()));
    }

    @Test void r2ReadsAndSignedDirectLinksUseStorageKeys() {
        when(storage.enabled()).thenReturn(true);
        when(storage.read("image")).thenReturn(new byte[]{1,2});
        when(storage.read("broken")).thenThrow(new IllegalStateException("offline"));
        when(storage.presignedUrl(eq("image"), any())).thenReturn("https://example.test/image");
        when(storage.presignedUrl(eq("support"), any())).thenReturn("https://example.test/support");
        var meta = Map.of(1, new McqQuestionMeta(1, Instant.now(), 2.0, 60, null,null,null,null,"image"),
                2, new McqQuestionMeta(2, null, null,null,null,null,null,null,null));
        var ref = mock(McqSubImageRepository.Reference.class);
        when(ref.getId()).thenReturn(99L);
        when(ref.getQuestionNumber()).thenReturn(1);
        when(ref.getStorageKey()).thenReturn("support");
        assertArrayEquals(new byte[]{1,2}, service.imageBytesByKey("image").orElseThrow());
        assertTrue(service.imageBytesByKey("broken").isEmpty());
        var result = service.links(meta, List.of(ref), Duration.ofMinutes(10));
        assertEquals("https://example.test/image", result.main().get(1));
        assertEquals("https://example.test/support", result.sub().get(99L));
        assertEquals(Map.of(1, List.of(99L)), McqExamQuestionService.subImageIds(List.of(ref)));
        var image = new McqExamQuestion();
        image.setStorageKey("image");
        assertEquals("https://example.test/image", service.imageRedirectUrl(image));
        when(questions.findIdsWithDatabaseImages()).thenReturn(List.of(2L,3L));
        assertEquals(List.of(2L,3L), service.idsWithDatabaseImages());
    }

    @Test void uploadingQuestionWithoutR2StoresBytesAndNormalizesTeacherMetadata() {
        when(questions.findByExamIdAndQuestionNumber(5L, 1)).thenReturn(Optional.empty());
        when(questions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var saved = service.save(exam(), 1, edit(), png(), false);
        assertEquals(3.5, saved.getWeight());
        assertEquals(60, saved.getTimeSeconds());
        assertEquals("Mechanics", saved.getUnits());
        assertEquals("image/png", saved.getContentType());
        assertArrayEquals(new byte[]{1,2,3}, saved.getData());
        assertNotNull(saved.getImageUpdatedAt());
        assertNull(saved.getStorageKey());
        assertThrows(IllegalArgumentException.class, () -> service.save(exam(), 0, edit(), png(), false));
    }

    @Test void deletingQuestionImagePreservesOtherMetadata() {
        var row = new McqExamQuestion();
        row.setData(new byte[]{1,2,3});
        row.setContentType("image/png");
        row.setImageUpdatedAt(Instant.now());
        when(questions.findByExamIdAndQuestionNumber(5L, 2)).thenReturn(Optional.of(row));
        when(questions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var saved = service.save(exam(), 2, edit(), null, true);
        assertNull(saved.getData());
        assertNull(saved.getContentType());
        assertNull(saved.getImageUpdatedAt());
        assertEquals(3.5, saved.getWeight());
    }

    @Test void r2ImageUploadAndMigrationAvoidKeepingDuplicateData() {
        when(storage.enabled()).thenReturn(true);
        when(questions.findByExamIdAndQuestionNumber(5L, 1)).thenReturn(Optional.empty());
        when(questions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var saved = service.save(exam(), 1, edit(), png(), false);
        assertNull(saved.getData());
        assertTrue(saved.getStorageKey().startsWith("mcq/exam-5/q1-"));
        verify(storage).put(eq(saved.getStorageKey()), org.mockito.AdditionalMatchers.aryEq(new byte[]{1,2,3}), eq("image/png"));
        var stored = new McqExamQuestion();
        stored.setExam(exam());
        stored.setQuestionNumber(3);
        stored.setContentType("image/jpeg");
        stored.setData(new byte[]{9});
        when(questions.findById(71L)).thenReturn(Optional.of(stored));
        assertTrue(service.moveImageToStorage(71L));
        assertNull(stored.getData());
        assertTrue(stored.getStorageKey().endsWith(".jpg"));
        assertFalse(service.moveImageToStorage(-1L));
    }

    @Test void supportImageCapAndInvalidFileDoNotCreatePartialRecords() {
        when(subs.findByExamIdAndQuestionNumberOrderById(5L, 1)).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.saveSubImages(5L,1,
                List.of(png(), png(),png(),png(),png()), null));
        assertThrows(IllegalArgumentException.class, () -> service.saveSubImages(5L,1,
                List.of(new MockMultipartFile("image","bad.svg","image/svg+xml",new byte[]{1})), null));
        verify(subs, never()).save(any());
        service.saveSubImages(5L, 1, List.of(png()), null);
        var capture = org.mockito.ArgumentCaptor.forClass(McqSubImage.class);
        verify(subs).save(capture.capture());
        assertEquals(5L, capture.getValue().getExamId());
        assertEquals(1, capture.getValue().getQuestionNumber());
        assertArrayEquals(new byte[]{1,2,3}, capture.getValue().getData());
    }

    @Test void copiesQuestionsAndReturnsSupportImageByOwnership() {
        service.copyForExam(2L, 5L);
        verify(questions).copyQuestions(2L,5L);
        verify(questions).copyTags(2L,5L);
        verify(questions).copySubImages(2L,5L);
        var image = new McqSubImage();
        when(subs.findByIdAndExamIdAndQuestionNumber(7L,5L,1)).thenReturn(Optional.of(image));
        assertSame(image, service.subImage(5L,1,7L).orElseThrow());
        assertNull(service.subImageRedirectUrl(image));
    }
}
