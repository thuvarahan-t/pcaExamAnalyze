package com.example.pcaExamAnalyze.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Rejects multipart voice requests before the servlet can parse or spool file parts. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class OfferFeedbackAudioRequestFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String type = request.getContentType();
        if ("POST".equals(request.getMethod()) && type != null && type.regionMatches(true, 0, "multipart/", 0, 10)
                && request.getRequestURI().equals(request.getContextPath() + OfferFeedbackVoiceController.PATH)) {
            response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"message\":\"This recording format is unsupported. Try recording again.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
