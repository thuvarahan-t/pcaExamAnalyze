package com.example.pcaExamAnalyze.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Replaces the raw Whitelabel 403 page. When a logged-in user reaches a path their
 * role can't access (e.g. a student opening a /teacher/** URL), send them to their own
 * dashboard instead of a dead-end error. Anyone not authenticated goes to the login page.
 */
@Component
public class RoleAwareAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal());

        String target;
        if (!authenticated) {
            target = "/login";
        } else {
            boolean teacher = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_TEACHER"));
            target = teacher ? "/teacher/dashboard" : "/student/dashboard";
        }
        response.sendRedirect(request.getContextPath() + target);
    }
}
