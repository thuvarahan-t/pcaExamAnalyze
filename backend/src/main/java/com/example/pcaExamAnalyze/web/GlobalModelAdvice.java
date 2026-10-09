package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.domain.User;
import com.example.pcaExamAnalyze.repo.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the logged-in user's display name to every Thymeleaf view (admin portal header).
 */
@ControllerAdvice(basePackages = "com.example.pcaExamAnalyze.web")
public class GlobalModelAdvice {

    private final UserRepository users;

    public GlobalModelAdvice(UserRepository users) {
        this.users = users;
    }

    @ModelAttribute
    public void addUserContext(org.springframework.ui.Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()));

        String displayName = null;

        if (authenticated) {
            User u = users.findByUsernameIgnoreCase(auth.getName()).orElse(null);
            displayName = u != null ? u.getFullName() : auth.getName();
        }

        model.addAttribute("currentUserName", displayName);
    }
}
