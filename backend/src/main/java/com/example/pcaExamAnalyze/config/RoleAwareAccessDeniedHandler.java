package com.example.pcaExamAnalyze.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import java.io.IOException;

/**
 * Replaces the raw Whitelabel 403 page: a logged-in user without access goes to the exam
 * portal; anyone not authenticated goes to the admin login.
 */
@Component
public class RoleAwareAccessDeniedHandler implements AccessDeniedHandler {
    private final SessionFlashMapManager flashMaps = new SessionFlashMapManager();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        String offerPath = request.getContextPath() + "/exam/packages";
        if (accessDeniedException instanceof CsrfException && "POST".equals(request.getMethod())
                && request.getRequestURI().equals(offerPath + "/feedback")) {
            var retry = new FlashMap();
            retry.setTargetRequestPath(offerPath);
            retry.put("feedbackError", "Your feedback form expired. Please fill it in again and resend.");
            flashMaps.saveOutputFlashMap(retry, request, response);
            response.sendRedirect(offerPath + "#student-feedback");
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal());

        String target = authenticated ? "/exam" : "/exam/admin-login";
        response.sendRedirect(request.getContextPath() + target);
    }
}
