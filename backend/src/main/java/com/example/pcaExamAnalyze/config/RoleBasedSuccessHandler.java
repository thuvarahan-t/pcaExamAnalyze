package com.example.pcaExamAnalyze.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Sends users to the right home after login: teachers to the teacher dashboard,
 * students to the student dashboard.
 */
@Component
public class RoleBasedSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) {
        boolean teacher = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TEACHER"));
        return teacher ? "/teacher/dashboard" : "/student/dashboard";
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        getRedirectStrategy().sendRedirect(request, response,
                determineTargetUrl(request, response, authentication));
    }
}
