package com.example.pcaExamAnalyze.config;

import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Sends failed logins back to the login page with a generic error. (Accounts are active
 * immediately now, so there is no "unverified" redirect.)
 */
@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    public LoginFailureHandler() {
        super("/login?error");
    }
}
