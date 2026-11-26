package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.PcaExamAnalyzeApplication;
import com.example.pcaExamAnalyze.domain.OfferFeedback;
import com.example.pcaExamAnalyze.service.OfferContentService;
import com.example.pcaExamAnalyze.service.OfferContentService.FeedbackInput;
import com.example.pcaExamAnalyze.service.R2ImageStorage;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OfferFeedbackPhotoIntegrationTest {
    private static final String OFFER = "/exam/packages";
    private static final String ADMIN = "/exam/admin/packages";
    private static final R2ImageStorage STORAGE = mock(R2ImageStorage.class);
    private static ConfigurableApplicationContext context;
    private static OfferContentService content;
    private static byte[] fixture;
    private static String base;

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
        base = "http://localhost:" + ((ServletWebServerApplicationContext) context).getWebServer().getPort();
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
        assertEquals(1, entry.getPhotoCount());
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
        assertThrows(IllegalArgumentException.class,
            () -> content.submitFeedback(input("Bad bytes"), List.of(photo(fixture), photo(new byte[]{1}))));
        var tooMany = assertThrows(IllegalArgumentException.class,
            () -> content.submitFeedback(input("Six photos"), Collections.nCopies(6, photo(fixture))));
        assertTrue(tooMany.getMessage().contains("up to 5 photos"));
        verifyNoInteractions(STORAGE);
        assertNull(find("Bad bytes"));
        assertNull(find("Six photos"));
    }

    @Test void failedSecondR2UploadCleansBothAttemptsAndKeepsTextOnlySubmissionAvailable() {
        String quote = "Text-only retry after a batch upload fails.";
        doNothing().doThrow(new IllegalStateException("simulated storage failure"))
            .when(STORAGE).put(anyString(), any(), anyString());
        var error = assertThrows(OfferContentService.FeedbackPhotoUnavailable.class,
            () -> content.submitFeedback(input(quote), Collections.nCopies(3, photo(fixture))));
        assertTrue(error.getMessage().contains("remove the photo"));
        assertNull(find(quote));
        assertCleanup(2);
        content.submitFeedback(input(quote), null);
        var textOnly = find(quote);
        assertNotNull(textOnly);
        assertNull(textOnly.getPhotoKey());
        assertNull(textOnly.getPhotoBytes());
        assertEquals(0, textOnly.getPhotoCount());
        assertEquals(OfferFeedback.Status.PENDING, textOnly.getStatus());
        assertUnavailable(textOnly.getId(), HttpStatus.NOT_FOUND);
        verify(STORAGE, never()).read(anyString());
    }

    @Test void rollbackAfterAnActualDatabaseInsertDeletesAllNewObjects() {
        String quote = "Feedback deliberately rolled back.";
        var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        transaction.executeWithoutResult(status -> {
            content.submitFeedback(input(quote), Collections.nCopies(3, photo(fixture)));
            assertNotNull(find(quote).getId());
            assertEquals(3, find(quote).getPhotoCount());
            status.setRollbackOnly();
        });
        assertCleanup(3);
        assertNull(find(quote));
    }

    @Test void multipartFeedbackRequiresCsrfAndPhotoRoutesRespectTeacherAccessAndModeration() throws Exception {
        String quote = "Feedback and five photos through the public form.";
        byte[] secondFixture = new ClassPathResource("package-posters/practical.jpg").getContentAsByteArray();
        var photos = List.of(fixture, secondFixture, fixture, secondFixture, fixture);
        try (var student = browser(); var teacher = browser()) {
            String token = csrf(get(student, OFFER));
            var denied = upload(student, "expired-token", quote, photos, true);
            assertEquals(403, denied.statusCode());
            assertTrue(denied.body().contains("feedback form expired"));
            assertNull(find(quote));
            verifyNoInteractions(STORAGE);

            var excess = upload(student, token, "Six photos rejected by the API.", Collections.nCopies(6, fixture), true);
            assertEquals(400, excess.statusCode());
            assertTrue(excess.body().contains("up to 5 photos"));
            verifyNoInteractions(STORAGE);

            var accepted = upload(student, token, quote, photos, true);
            assertEquals(200, accepted.statusCode());
            assertEquals("{}", accepted.body());
            var entry = find(quote);
            assertEquals(5, entry.getPhotoCount());
            assertEquals(1, content.feedback(true).stream().filter(row -> row.getId().equals(entry.getId())).count());
            var keys = ArgumentCaptor.forClass(String.class);
            var bytes = ArgumentCaptor.forClass(byte[].class);
            verify(STORAGE, times(5)).put(keys.capture(), bytes.capture(), eq("image/jpeg"));
            assertEquals(5, new HashSet<>(keys.getAllValues()).size());
            for (int index = 0; index < photos.size(); index++) {
                var metadata = entry.photoAt(index);
                String key = keys.getAllValues().get(index);
                byte[] photo = bytes.getAllValues().get(index);
                assertEquals(key, metadata.key());
                assertTrue(key.matches("offer-feedback/[a-f0-9-]{36}\\.jpg"));
                assertEquals(photo.length, metadata.bytes());
                assertTrue(photo.length <= 100_000);
                assertTrue(metadata.width() > 0 && metadata.height() > 0);
                when(STORAGE.read(key)).thenReturn(photo);
            }
            String publicPhoto = OFFER + "/feedback/" + entry.getId() + "/photo";
            String teacherPhoto = ADMIN + "/feedback/" + entry.getId() + "/photo";
            String status = ADMIN + "/feedback/" + entry.getId() + "/status";

            assertEquals(404, get(student, publicPhoto).statusCode());
            assertEquals(404, get(student, publicPhoto + "/4").statusCode());
            assertEquals(302, get(student, teacherPhoto + "/4").statusCode());
            verify(STORAGE, never()).read(anyString());
            String loginCsrf = csrf(get(teacher, "/exam/admin-login"));
            assertEquals(302, post(teacher, "/login", loginCsrf,
                "username=feedback-admin&password=FeedbackTest123%21").statusCode());
            assertPhoto(getBytes(teacher, teacherPhoto), bytes.getAllValues().getFirst());
            assertPhoto(getBytes(teacher, teacherPhoto + "/4"), bytes.getAllValues().getLast());

            var adminPage = get(teacher, ADMIN);
            var pending = Jsoup.parse(adminPage.body()).selectFirst(".offer-review-queue article:contains(" + quote + ")");
            assertNotNull(pending);
            assertEquals(indexedPaths(teacherPhoto, photos.size()), pending.select(".offer-moderation-photo img").eachAttr("src"));
            String adminCsrf = csrf(adminPage);
            assertEquals(302, post(teacher, status, adminCsrf, "status=PUBLISHED").statusCode());
            var published = Jsoup.parse(get(student, OFFER).body()).selectFirst(".student-quote:contains(" + quote + ")");
            assertNotNull(published);
            assertEquals(indexedPaths(publicPhoto, photos.size()), published.select(".feedback-quote-photo").eachAttr("src"));
            for (int index = 0; index < photos.size(); index++) {
                assertPhoto(getBytes(student, publicPhoto + "/" + index), bytes.getAllValues().get(index));
            }
            assertEquals(404, get(student, publicPhoto + "/-1").statusCode());
            assertEquals(404, get(student, publicPhoto + "/5").statusCode());
            assertEquals(302, post(teacher, status, adminCsrf, "status=HIDDEN").statusCode());
            var hidden = get(student, publicPhoto);
            assertEquals(404, hidden.statusCode());
            assertTrue(hidden.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
            assertEquals(404, get(student, publicPhoto + "/4").statusCode());
            verify(STORAGE, times(7)).read(anyString());
        }
    }

    @Test void readsLegacyPhotoMetadataAndSupportsTheOriginalUrl() throws Exception {
        String quote = "Feedback from before the multi-photo change.";
        content.addFeedback(input(quote), OfferFeedback.Status.PUBLISHED);
        long id = find(quote).getId();
        String key = "offer-feedback/legacy.jpg";
        context.getBean(JdbcTemplate.class).update("update offer_feedback set photo_key = ?, photo_bytes = ? where id = ?",
            key, fixture.length, id);
        when(STORAGE.read(key)).thenReturn(fixture);
        assertEquals(1, find(quote).getPhotoCount());
        try (var student = browser()) {
            String path = OFFER + "/feedback/" + id + "/photo";
            assertPhoto(getBytes(student, path), fixture);
            assertPhoto(getBytes(student, path + "/0"), fixture);
            assertEquals(404, get(student, path + "/1").statusCode());
            verify(STORAGE, times(2)).read(key);
        }
    }

    @Test void multipartErrorsAreRecoverableAndNativeFeedbackRetainsTheDraft() throws Exception {
        String quote = "Keep this draft when the photo fails.";
        try (var student = browser()) {
            String token = csrf(get(student, OFFER));
            var invalid = upload(student, token, quote, new byte[]{1, 2, 3}, true);
            assertEquals(400, invalid.statusCode());
            assertTrue(invalid.body().contains("valid JPEG or PNG"));
            verifyNoInteractions(STORAGE);

            doThrow(new IllegalStateException("simulated outage")).when(STORAGE).put(anyString(), any(), anyString());
            var unavailable = upload(student, token, quote, fixture, true);
            assertEquals(503, unavailable.statusCode());
            assertTrue(unavailable.body().contains("remove the photo"));
            var nativePost = upload(student, token, quote, fixture, false);
            assertEquals(302, nativePost.statusCode());
            assertTrue(nativePost.headers().firstValue("Location").orElseThrow().endsWith(OFFER + "#student-feedback"));
            var offer = Jsoup.parse(get(student, OFFER).body());
            assertTrue(offer.selectFirst(".feedback-form [role=alert]").text().contains("remove the photo"));
            assertEquals(quote, offer.selectFirst(".feedback-form textarea[name=comment]").val());
            assertNull(find(quote));

            assertEquals(302, post(student, OFFER + "/feedback", token,
                "name=Feedback+Student&school=Test+School&district=Jaffna&comment=" + URLEncoder.encode(quote, StandardCharsets.UTF_8))
                .statusCode());
            assertNull(find(quote).getPhotoKey());
            verify(STORAGE, timeout(5000).times(2)).deleteQuietly(startsWith("offer-feedback/"));
        }
    }

    private static HttpClient browser() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }

    private static HttpResponse<String> get(HttpClient browser, String path) throws Exception {
        return browser.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<byte[]> getBytes(HttpClient browser, String path) throws Exception {
        return browser.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String csrf(HttpResponse<String> response) {
        return Jsoup.parse(response.body()).selectFirst("input[name=_csrf]").val();
    }

    private static HttpResponse<String> post(HttpClient browser, String path, String token, String fields) throws Exception {
        return browser.send(request(path).header("X-CSRF-TOKEN", token)
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(fields)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> upload(HttpClient browser, String token, String quote, byte[] bytes, boolean json) throws Exception {
        return upload(browser, token, quote, List.of(bytes), json);
    }

    private static HttpResponse<String> upload(HttpClient browser, String token, String quote, List<byte[]> files, boolean json) throws Exception {
        String boundary = "feedback-integration-boundary";
        String fields = multipartField(boundary, "name", "Feedback Student") + multipartField(boundary, "school", "Test School")
            + multipartField(boundary, "district", "Jaffna") + multipartField(boundary, "comment", quote);
        var parts = new ArrayList<HttpRequest.BodyPublisher>();
        parts.add(HttpRequest.BodyPublishers.ofString(fields));
        for (byte[] file : files) {
            parts.add(HttpRequest.BodyPublishers.ofString("--" + boundary
                + "\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"feedback.jpg\""
                + "\r\nContent-Type: image/jpeg\r\n\r\n"));
            parts.add(HttpRequest.BodyPublishers.ofByteArray(file));
            parts.add(HttpRequest.BodyPublishers.ofString("\r\n"));
        }
        parts.add(HttpRequest.BodyPublishers.ofString("--" + boundary + "--\r\n"));
        var multipart = HttpRequest.BodyPublishers.concat(parts.toArray(HttpRequest.BodyPublisher[]::new));
        var request = request(OFFER + "/feedback").header("X-CSRF-TOKEN", token)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary).POST(multipart);
        if (json) request.header("X-Feedback-Upload", "true").header("Accept", "application/json");
        return browser.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String multipartField(String boundary, String name, String value) {
        return "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n";
    }

    private static List<String> indexedPaths(String path, int count) {
        return IntStream.range(0, count).mapToObj(index -> path + "/" + index).toList();
    }

    private static HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(base + path));
    }

    private static void assertPhoto(HttpResponse<byte[]> response, byte[] expected) {
        assertEquals(200, response.statusCode());
        assertArrayEquals(expected, response.body());
        assertEquals("image/jpeg", response.headers().firstValue("Content-Type").orElseThrow());
        assertTrue(response.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
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

    private static void assertCleanup(int attempts) {
        var keys = ArgumentCaptor.forClass(String.class);
        verify(STORAGE, times(attempts)).put(keys.capture(), any(), eq("image/jpeg"));
        assertEquals(attempts, new HashSet<>(keys.getAllValues()).size());
        for (String key : keys.getAllValues()) verify(STORAGE, timeout(5000)).deleteQuietly(key);
        verify(STORAGE, times(attempts)).deleteQuietly(anyString());
    }
}
