package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.domain.User;
import com.example.pcaExamAnalyze.repo.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the logged-in user and role flags to every Thymeleaf view so the
 * shared navbar can render the correct dashboard link without the security dialect.
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

        boolean teacher = false;
        boolean student = false;
        String displayName = null;

        if (authenticated) {
            teacher = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_TEACHER"));
            student = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"));
            User u = users.findByUsernameIgnoreCase(auth.getName()).orElse(null);
            displayName = u != null ? u.getFullName() : auth.getName();
        }

        model.addAttribute("isAuthenticated", authenticated);
        model.addAttribute("isTeacher", teacher);
        model.addAttribute("isStudent", student);
        model.addAttribute("currentUserName", displayName);
    }
}
