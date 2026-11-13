package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.service.ClassPackageService;
import com.example.pcaExamAnalyze.web.dto.ExamStudentDetailsForm;
import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Controller
@RequestMapping("/exam/packages")
public class ClassPackageController {
    private static final String DETAILS_KEY = "packageEnquiryDetails";
    private static final String COOKIE = "pca_package_details";
    private final ClassPackageService service;
    public ClassPackageController(ClassPackageService service) { this.service = service; }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<?> unavailable(org.springframework.web.server.ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore())
            .body(Map.of("error", "This package is no longer available."));
    }
    public record Details(String name, String registrationId) implements java.io.Serializable {}

    @GetMapping
    public String page(HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        model.addAttribute("campaign", service.campaign()); model.addAttribute("packages", service.cards(false));
        model.addAttribute("serverNow", Instant.now()); model.addAttribute("details", details(request));
        model.addAttribute("recent", service.recentPurchases());
        return "exam/packages";
    }
    @GetMapping("/status") @ResponseBody
    public ResponseEntity<?> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
            "serverNow", Instant.now(), "endsAt", service.campaign().getEndsAt(), "packages", service.cards(false),
            "recent", service.recentPurchases()));
    }
    @GetMapping("/purchases") @ResponseBody
    public ResponseEntity<?> purchases(@RequestParam(required = false) String beforeAt,
            @RequestParam(required = false) Long beforeId, @RequestParam(defaultValue = "20") int size) {
        Instant at = null;
        if (beforeAt != null && !beforeAt.isBlank()) {
            try { at = Instant.parse(beforeAt); }
            catch (java.time.format.DateTimeParseException e) {
                return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("error", "Invalid cursor."));
            }
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.purchasePage(at, beforeId, size));
    }
    @GetMapping("/{id}/poster")
    public ResponseEntity<byte[]> poster(@PathVariable Long id) {
        var image = service.poster(id, false);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).header("X-Content-Type-Options", "nosniff")
            .contentType(MediaType.parseMediaType(image.getContentType())).body(image.getData());
    }
    @PostMapping("/{id}/enquiry") @ResponseBody
    public ResponseEntity<?> enquiry(@PathVariable Long id, @RequestParam String name,
            @RequestParam String registrationId, HttpServletRequest request, HttpServletResponse response) {
        try {
            service.assertEnquiryAllowed(id);
            var details = new Details(ClassPackageService.studentName(name), ClassPackageService.registrationId(registrationId));
            request.getSession().setAttribute(DETAILS_KEY, details);
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (details.name() + "\u001f" + details.registrationId()).getBytes(StandardCharsets.UTF_8));
            var cookie = new Cookie(COOKIE, encoded); cookie.setPath("/exam/packages"); cookie.setHttpOnly(true);
            cookie.setSecure(request.isSecure()); cookie.setAttribute("SameSite", "Lax"); cookie.setMaxAge(365 * 86400); response.addCookie(cookie);
            String packageName = service.publicPackage(id).getName();
            String message = "Hi, my name is " + details.name() + ". I'd like to purchase the " + packageName + "."
                + "\n\n*Name:* " + details.name()
                + "\n*Registration No:* " + details.registrationId()
                + "\n*Package:* " + packageName;
            String url = "https://wa.me/" + service.campaign().getWhatsappNumber() + "?text="
                + URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("url", url, "message", message, "details", details));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("error", e.getMessage()));
        }
    }
    private Details details(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null && session.getAttribute(DETAILS_KEY) instanceof Details saved) return saved;
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) {
            if (!COOKIE.equals(cookie.getName()) || cookie.getValue().length() > 2000) continue;
            try {
                var parts = new String(Base64.getUrlDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8).split("\u001f", -1);
                if (parts.length == 2) return new Details(ClassPackageService.studentName(parts[0]), ClassPackageService.registrationId(parts[1]));
            } catch (IllegalArgumentException ignored) { /* Fall back to exam details. */ }
        }
        if (session != null && session.getAttribute(ExamDetailsCookie.SESSION_KEY) instanceof ExamStudentDetailsForm exam)
            return new Details(exam.getFullName(), exam.getRegistrationId());
        return new Details("", "");
    }
}
