package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.service.ClassPackageService;
import com.example.pcaExamAnalyze.service.ClassPackageService.Contact;
import com.example.pcaExamAnalyze.domain.OfferFeedback.Status;
import com.example.pcaExamAnalyze.service.OfferContentService;
import com.example.pcaExamAnalyze.service.OfferContentService.FeedbackInput;
import com.example.pcaExamAnalyze.service.OfferContentService.PhotoVariant;
import com.example.pcaExamAnalyze.report.ExamReportService;
import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Controller
@RequestMapping("/exam/packages")
public class ClassPackageController {
    private static final String DETAILS_KEY = "packageContactDetails";
    private static final String COOKIE = "pca_package_contact";
    private final ClassPackageService service;
    private final OfferContentService content;
    public ClassPackageController(ClassPackageService service, OfferContentService content) {
        this.service = service;
        this.content = content;
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<?> unavailable(org.springframework.web.server.ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore())
            .body(Map.of("error", "This package is no longer available."));
    }

    @GetMapping
    public String page(HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        model.addAttribute("campaign", service.campaign()); model.addAttribute("packages", service.cards(false));
        model.addAttribute("serverNow", Instant.now()); model.addAttribute("details", details(request));
        model.addAttribute("recent", service.recentPurchases());
        model.addAttribute("events", content.events(false));
        model.addAttribute("feedback", content.feedback(false));
        model.addAttribute("districts", ExamReportService.DISTRICTS);
        return "exam/packages";
    }

    @GetMapping("/event-photos/{id}")
    public ResponseEntity<byte[]> eventPhoto(@PathVariable Long id, @RequestParam(defaultValue = "hero") String variant,
            WebRequest request) {
        var size = switch (variant) {
            case "hero" -> PhotoVariant.HERO;
            case "thumb" -> PhotoVariant.THUMB;
            default -> throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST);
        };
        String etag = "\"event-photo-web-v1-" + id + "-" + variant + "\"";
        var cache = CacheControl.noCache();
        if (request.getHeader(HttpHeaders.IF_NONE_MATCH) != null) {
            content.requirePublishedPhoto(id);
            if (request.checkNotModified(etag)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cache).build();
            }
        }
        var photo = content.webPhoto(id, size);
        return ResponseEntity.ok().eTag(etag).cacheControl(cache).header("X-Content-Type-Options", "nosniff")
            .contentType(MediaType.parseMediaType(photo.contentType())).body(photo.data());
    }

    @PostMapping("/feedback")
    public String feedback(@RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String school, @RequestParam(defaultValue = "") String district,
            @RequestParam(defaultValue = "") String comment, RedirectAttributes redirect) {
        try {
            var input = new FeedbackInput(name, school, district, "", comment);
            content.addFeedback(input, Status.PENDING);
            redirect.addFlashAttribute("feedbackSuccess", true);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("feedbackError", e.getMessage());
            redirect.addFlashAttribute("feedbackDraft", new FeedbackInput(name, school, district, "", comment));
        }
        return "redirect:/exam/packages#student-feedback";
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
    public ResponseEntity<?> enquiry(@PathVariable Long id, @RequestParam String firstName,
            @RequestParam String lastName, @RequestParam String phoneNumber, @RequestParam UUID requestKey,
            HttpServletRequest request, HttpServletResponse response) {
        try {
            var contact = Contact.validated(firstName, lastName, phoneNumber);
            var interest = service.recordContactEnquiry(id, contact, requestKey.toString());
            var details = new Contact(interest.getFirstName(), interest.getLastName(), interest.getPhoneNumber());
            request.getSession().setAttribute(DETAILS_KEY, details);
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.join("\u001f", details.firstName(), details.lastName(), details.phoneNumber()).getBytes(StandardCharsets.UTF_8));
            var cookie = new Cookie(COOKIE, encoded); cookie.setPath("/exam/packages"); cookie.setHttpOnly(true);
            cookie.setSecure(request.isSecure()); cookie.setAttribute("SameSite", "Lax"); cookie.setMaxAge(365 * 86400); response.addCookie(cookie);
            String packageName = service.publicPackage(id).getName();
            String message = "Hi, I'd like to purchase the " + packageName + "."
                + "\n\n*Student's name:* " + details.firstName()
                + "\n*Father's name:* " + details.lastName()
                + "\n*Contact number:* " + details.phoneNumber()
                + "\n*Package:* " + packageName
                + "\n*Enquiry Ref:* " + interest.getReference();
            String url = "https://wa.me/" + service.campaign().getWhatsappNumber() + "?text="
                + URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("url", url, "message", message, "details", details, "reference", interest.getReference()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("error", e.getMessage()));
        }
    }
    private Contact details(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null && session.getAttribute(DETAILS_KEY) instanceof Contact saved) return saved;
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) {
            if (!COOKIE.equals(cookie.getName()) || cookie.getValue().length() > 2000) continue;
            try {
                var parts = new String(Base64.getUrlDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8).split("\u001f", -1);
                if (parts.length == 3) return Contact.validated(parts[0], parts[1], parts[2]);
            } catch (IllegalArgumentException ignored) { /* Ignore invalid saved contact details. */ }
        }
        return new Contact("", "", "");
    }
}
