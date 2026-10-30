package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.domain.McqExamQuestion;
import com.example.pcaExamAnalyze.domain.McqSubImage;
import com.example.pcaExamAnalyze.web.dto.ExamStudentDetailsForm;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.URI;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Browser restart / student identity and image delivery regressions. */
class StudentDetailsCookieAndImageTest {
    private ExamStudentDetailsForm form() {
        var form = new ExamStudentDetailsForm();
        form.setEmail("  Student@Example.TEST  ");
        form.setRegistrationId(" bpF-2027-55 ");
        form.setNic(" 2000 0000 0001 ");
        form.setFullName("  mITHUKSHAN sIVA-KUMAR  ");
        form.setBatch(" 2027 A/L ");
        form.setSchool("  Example  College  ");
        form.setStream(" Physical Science ");
        form.setDistrict(" Jaffna ");
        return form;
    }

    @Test void normalizationMaintainsIdentityAndUnicodeNames() {
        var input = form();
        input.normalize();
        assertEquals("student@example.test", input.getEmail());
        assertEquals("BPF-2027-55", input.getRegistrationId());
        assertEquals("200000000001", input.getNic());
        assertEquals("Mithukshan Siva-Kumar", input.getFullName());
        assertEquals("Example College", input.getSchool());
        assertEquals("2027 A/L", input.getBatch());
        assertEquals("Physical Science", input.getStream());
        assertEquals("Jaffna", input.getDistrict());

        var missing = new ExamStudentDetailsForm();
        missing.normalize();
        assertEquals("", missing.getFullName());
        assertEquals("", missing.getEmail());
        assertEquals("", missing.getNic());
    }

    @Test void writeRestoreAndClearStudentCookieSurviveSessionRestart() throws Exception {
        var loginRequest = new MockHttpServletRequest("POST", "/exam/details");
        loginRequest.setSecure(true);
        var response = new MockHttpServletResponse();
        var input = form();
        ExamDetailsCookie.write(loginRequest, response, input);
        Cookie original = response.getCookies()[0];
        assertEquals("/exam", original.getPath());
        assertTrue(original.isHttpOnly());
        assertTrue(original.getSecure());
        assertTrue(original.getMaxAge() > 86400);
        assertEquals("Lax", original.getAttribute("SameSite"));

        var resumed = new MockHttpServletRequest("GET", "/exam/session/100");
        resumed.setCookies(original);
        var chain = new MockFilterChain();
        new ExamDetailsCookie().doFilter(resumed, new MockHttpServletResponse(), chain);
        assertNotNull(resumed.getSession(false));
        var restored = (ExamStudentDetailsForm) resumed.getSession(false).getAttribute(ExamDetailsCookie.SESSION_KEY);
        assertNotNull(restored);
        assertEquals("BPF-2027-55", restored.getRegistrationId());
        assertEquals("200000000001", restored.getNic());

        var clearResponse = new MockHttpServletResponse();
        ExamDetailsCookie.clear(new MockHttpServletRequest(), clearResponse);
        assertEquals(0, clearResponse.getCookies()[0].getMaxAge());
        assertFalse(clearResponse.getCookies()[0].getSecure());
    }

    @Test void malformedCookieCannotBecomeTrustedStudentSession() throws Exception {
        for (String value : Arrays.asList("not-base64!!", "", "YWJj", "////", "AA")) {
            var request = new MockHttpServletRequest("GET", "/exam/session/100");
            request.setCookies(new Cookie("pca_exam_details", value));
            new ExamDetailsCookie().doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
            assertNull(request.getSession(false), "invalid cookie must not create session: " + value);
        }
    }

    @Test void adminAndUnrelatedRoutesDoNotReadStudentCookie() throws Exception {
        var filter = new ExamDetailsCookie();
        assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/login")));
        assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/exam/admin")));
        assertFalse(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/exam/results")));
    }

    @Test void questionImagesUsePrivateCachingForBothDatabaseAndR2() {
        var image = new McqExamQuestion();
        image.setContentType("image/png");
        image.setData(new byte[]{1, 2, 3});
        var inline = QuestionImageResponses.of(image, null);
        assertEquals(HttpStatus.OK, inline.getStatusCode());
        assertArrayEquals(new byte[]{1, 2, 3}, inline.getBody());
        assertEquals("image/png", inline.getHeaders().getContentType().toString());
        assertTrue(inline.getHeaders().getCacheControl().contains("private"));

        var redirect = QuestionImageResponses.of(image, "https://images.example.test/001");
        assertEquals(HttpStatus.FOUND, redirect.getStatusCode());
        assertEquals(URI.create("https://images.example.test/001"), redirect.getHeaders().getLocation());
        assertNull(redirect.getBody());
        assertTrue(redirect.getHeaders().getCacheControl().contains("private"));

        var sub = new McqSubImage();
        sub.setContentType("image/jpeg");
        sub.setData(new byte[]{9, 8});
        assertArrayEquals(new byte[]{9, 8}, QuestionImageResponses.of(sub, null).getBody());
        assertEquals(HttpStatus.FOUND, QuestionImageResponses.of(sub, "https://images.example.test/sub").getStatusCode());
    }
}
