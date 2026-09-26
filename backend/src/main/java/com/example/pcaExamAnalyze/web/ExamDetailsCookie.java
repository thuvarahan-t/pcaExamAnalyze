package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.web.dto.ExamStudentDetailsForm;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Keeps the MCQ student's details in a long-lived browser cookie as well as the session,
 * so a student enters them once and they survive closing the browser and server restarts.
 * When a request to /exam arrives without details in the session, they are restored from
 * the cookie. Tampering gains nothing: anyone can type any details into the form anyway,
 * and every value is re-checked here before it is used.
 */
@Component
public class ExamDetailsCookie extends OncePerRequestFilter {

    static final String SESSION_KEY = "mcqStudentDetails";
    private static final String COOKIE = "pca_exam_details";
    private static final int MAX_AGE = (int) Duration.ofDays(365).toSeconds();
    private static final String SEP = "\u001f";

    static void write(HttpServletRequest request, HttpServletResponse response, ExamStudentDetailsForm form) {
        String raw = String.join(SEP, form.getEmail(), form.getRegistrationId(), form.getNic(), form.getFullName(),
                form.getBatch(), form.getSchool(), form.getStream(), form.getDistrict());
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        response.addCookie(cookie(request, value, MAX_AGE));
    }

    static void clear(HttpServletRequest request, HttpServletResponse response) {
        response.addCookie(cookie(request, "", 0));
    }

    private static Cookie cookie(HttpServletRequest request, String value, int maxAge) {
        Cookie cookie = new Cookie(COOKIE, value);
        cookie.setPath("/exam");
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }

    private static ExamStudentDetailsForm read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (!COOKIE.equals(cookie.getName()) || cookie.getValue().isBlank()) continue;
            try {
                String raw = new String(Base64.getUrlDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
                String[] parts = raw.split(SEP, -1);
                if (parts.length != 8) return null;
                ExamStudentDetailsForm form = new ExamStudentDetailsForm();
                form.setEmail(parts[0]);
                form.setRegistrationId(parts[1]);
                form.setNic(parts[2]);
                form.setFullName(parts[3]);
                form.setBatch(parts[4]);
                form.setSchool(parts[5]);
                form.setStream(parts[6]);
                form.setDistrict(parts[7]);
                form.normalize();
                boolean valid = form.getRegistrationId().matches("^[A-Za-z0-9-]{3,30}$")
                        && form.getNic().matches("^[0-9]{12}$")
                        && !form.getFullName().isBlank() && !form.getEmail().isBlank()
                        && !form.getBatch().isBlank() && !form.getStream().isBlank();
                return valid ? form : null;
            } catch (IllegalArgumentException badCookie) {
                return null;
            }
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/exam") || path.startsWith("/exam/admin");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SESSION_KEY) == null) {
            ExamStudentDetailsForm saved = read(request);
            if (saved != null) request.getSession(true).setAttribute(SESSION_KEY, saved);
        }
        chain.doFilter(request, response);
    }
}
