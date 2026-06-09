package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.service.UserService;
import com.example.pcaExamAnalyze.web.dto.RegisterForm;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
public class AuthController {

    /** Sri Lanka's 25 administrative districts, for the registration dropdown. */
    private static final List<String> DISTRICTS = List.of(
            "Colombo", "Gampaha", "Kalutara", "Kandy", "Matale", "Nuwara Eliya",
            "Galle", "Matara", "Hambantota", "Jaffna", "Kilinochchi", "Mannar",
            "Vavuniya", "Mullaitivu", "Batticaloa", "Ampara", "Trincomalee",
            "Kurunegala", "Puttalam", "Anuradhapura", "Polonnaruwa", "Badulla",
            "Monaragala", "Ratnapura", "Kegalle");

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @org.springframework.web.bind.annotation.ModelAttribute("districts")
    public List<String> districts() {
        return DISTRICTS;
    }

    @ModelAttribute("batches")
    public List<String> batches() {
        return List.of("2026 Repeat Batch", "2026 Proper Batch", "2027 Batch");
    }

    @GetMapping("/")
    public String home(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
            boolean teacher = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_TEACHER"));
            return teacher ? "redirect:/teacher/dashboard" : "redirect:/student/dashboard";
        }
        addRegisterFormIfMissing(model);
        return "index";
    }

    @GetMapping("/login")
    public String login(Model model) {
        addRegisterFormIfMissing(model);
        model.addAttribute("authModal", "login");
        return "index";
    }

    @GetMapping("/auth/login")
    public String loginPage() {
        return "login";
    }

    // ---------- Registration ----------

    @GetMapping("/register")
    public String registerForm(Model model) {
        addRegisterFormIfMissing(model);
        model.addAttribute("authModal", "register");
        return "index";
    }

    @GetMapping("/auth/register")
    public String registerPage(Model model) {
        addRegisterFormIfMissing(model);
        return "register";
    }

    @GetMapping("/register/username-available")
    @ResponseBody
    public Map<String, Boolean> usernameAvailable(@RequestParam String username) {
        boolean valid = username != null && username.matches("^[a-z0-9]{3,30}$");
        return Map.of("available", valid && !userService.usernameTaken(username));
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form,
                           BindingResult binding,
                           Model model) {
        if (!form.passwordsMatch()) {
            binding.rejectValue("confirmPassword", "mismatch", "Passwords do not match");
        }
        if (userService.usernameTaken(form.getUsername())) {
            binding.rejectValue("username", "taken", "That username is already taken");
        }
        if (binding.hasErrors()) {
            model.addAttribute("authModal", "register");
            return "index";
        }
        userService.registerStudent(form);
        // No email verification — the account is active, so send them straight to sign in.
        return "redirect:/login?registered";
    }

    private void addRegisterFormIfMissing(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new RegisterForm());
        }
    }

    // ---------- Forgot password (verified by NIC, no email) ----------

    @GetMapping("/forgot-password")
    public String forgotForm(Model model) {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String resetPassword(@RequestParam String username,
                                @RequestParam String nic,
                                @RequestParam String password,
                                @RequestParam String confirmPassword,
                                Model model) {
        model.addAttribute("username", username);
        model.addAttribute("nic", nic);

        if (password == null || password.length() < 6) {
            model.addAttribute("flashError", "Password must be at least 6 characters.");
            return "forgot-password";
        }
        if (!password.equals(confirmPassword)) {
            model.addAttribute("flashError", "Passwords do not match.");
            return "forgot-password";
        }
        if (!userService.resetPasswordByNic(username, nic, password)) {
            model.addAttribute("flashError", "Username and NIC do not match any account.");
            return "forgot-password";
        }
        return "redirect:/login?reset";
    }
}
