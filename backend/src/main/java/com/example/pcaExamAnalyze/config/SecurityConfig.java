package com.example.pcaExamAnalyze.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // ~60 days — how long a "remember me" login stays valid without re-entering credentials.
    private static final int REMEMBER_ME_DAYS = 60;

    private final RoleBasedSuccessHandler successHandler;
    private final LoginFailureHandler failureHandler;
    private final RoleAwareAccessDeniedHandler accessDeniedHandler;

    // Stable secret that signs the remember-me cookie. Set REMEMBER_ME_KEY in prod so the
    // cookie keeps working across restarts/deploys; the default is only for local dev.
    @Value("${pca.remember-me.key:pca-local-dev-remember-me-key}")
    private String rememberMeKey;

    public SecurityConfig(RoleBasedSuccessHandler successHandler, LoginFailureHandler failureHandler,
                          RoleAwareAccessDeniedHandler accessDeniedHandler) {
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/", "/login", "/register", "/auth/login", "/auth/register",
                        "/register/username-available", "/forgot-password",
                        "/exam", "/exam/", "/exam/p/**", "/exam/details", "/exam/details/clear",
                        "/exam/session/**", "/exam/admin-login", "/exam/result", "/exam/results/**",
                        "/css/**", "/js/**", "/img/**", "/favicon.ico",
                        "/webjars/**", "/h2-console/**"
                ).permitAll()
                .requestMatchers("/exam/admin", "/exam/admin/**").hasRole("TEACHER")
                .requestMatchers("/teacher/**").hasRole("TEACHER")
                .requestMatchers("/student/**").hasRole("STUDENT")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .deleteCookies("JSESSIONID", "remember-me")
                .permitAll()
            )
            // Keep users signed in until they explicitly log out: every login issues a
            // signed remember-me cookie that re-authenticates after the session expires.
            .rememberMe(remember -> remember
                .key(rememberMeKey)
                .rememberMeCookieName("remember-me")
                .alwaysRemember(true)
                .tokenValiditySeconds(REMEMBER_ME_DAYS * 24 * 60 * 60)
            )
            // Send role-mismatched users to their own dashboard instead of a raw 403 page.
            .exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler))
            // Allow the H2 console (dev profile) to render inside frames.
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            // The H2 console (dev profile) does not send CSRF tokens.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"));

        return http.build();
    }
}
