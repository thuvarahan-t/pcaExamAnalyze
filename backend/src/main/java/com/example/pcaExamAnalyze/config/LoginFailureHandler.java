package com.example.pcaExamAnalyze.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Sends failed logins back to the login page with a generic error. (Accounts are active
 * immediately now, so there is no "unverified" redirect.)
 */
@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    public LoginFailureHandler() {
        super("/login?error");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception)
            throws IOException, ServletException {
        if ("true".equals(request.getParameter("adminLogin"))) {
            getRedirectStrategy().sendRedirect(request, response, "/exam/admin-login?error");
            return;
        }
        super.onAuthenticationFailure(request, response, exception);
    }
}
