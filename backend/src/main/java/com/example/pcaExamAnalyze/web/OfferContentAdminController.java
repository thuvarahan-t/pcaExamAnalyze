package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.domain.OfferEventPhoto;
import com.example.pcaExamAnalyze.domain.OfferFeedback.Status;
import com.example.pcaExamAnalyze.service.OfferContentService;
import com.example.pcaExamAnalyze.service.OfferContentService.FeedbackInput;
import com.example.pcaExamAnalyze.service.OfferContentService.FeedbackImportRow;
import java.io.IOException;
import java.util.List;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/exam/admin/packages")
public class OfferContentAdminController {
    private static final String OFFER = "redirect:/exam/admin/packages";
    private static final String EVENTS = OFFER + "#event-highlights";
    private static final String FEEDBACK = OFFER + "#student-feedback";
    private static final List<String> FEEDBACK_HEADERS = List.of("Student Name", "School", "District", "Feedback");
    private static final List<String> LEGACY_FEEDBACK_HEADERS = List.of("Student Name", "School", "District", "Package", "Feedback");
    private final OfferContentService content;

    public OfferContentAdminController(OfferContentService content) {
        this.content = content;
    }

    @PostMapping({"/events", "/events/{id}"})
    public String saveEvent(@PathVariable(required = false) Long id,
            @RequestParam(defaultValue = "") String title,
            @RequestParam(defaultValue = "") String venue, @RequestParam(defaultValue = "") String description,
            @RequestParam(required = false) List<MultipartFile> photos, RedirectAttributes redirect) {
        if (id != null) redirect.addFlashAttribute("activeEventId", id);
        try {
            content.saveEvent(id, title, venue, description, photos);
            redirect.addFlashAttribute("eventSuccess", id == null ? "Event highlight published." : "Event highlight updated.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("eventError", e.getMessage());
        }
        return EVENTS;
    }

    @PostMapping("/events/{eventId}/photos/{photoId}/remove")
    public String removePhoto(@PathVariable Long eventId, @PathVariable Long photoId, RedirectAttributes redirect) {
        redirect.addFlashAttribute("activeEventId", eventId);
        try {
            content.removeEventPhoto(eventId, photoId);
            redirect.addFlashAttribute("eventSuccess", "Event photo removed.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("eventError", e.getMessage());
        }
        return EVENTS;
    }

    @PostMapping("/events/{id}/visibility")
    public String eventVisibility(@PathVariable Long id, @RequestParam boolean published, RedirectAttributes redirect) {
        content.setEventPublished(id, published);
        redirect.addFlashAttribute("eventSuccess", published ? "Event highlight published." : "Event highlight hidden.");
        return EVENTS;
    }

    @GetMapping("/event-photos/{id}")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        return photoResponse(content.photo(id, true));
    }

    static ResponseEntity<byte[]> photoResponse(OfferEventPhoto photo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options", "nosniff")
            .contentType(MediaType.parseMediaType(photo.getContentType())).body(photo.getData());
    }

    @PostMapping("/video")
    public String video(@RequestParam String video, RedirectAttributes redirect) {
        try {
            content.saveVideo(video);
            redirect.addFlashAttribute("videoSuccess", "Offer video updated.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("videoError", e.getMessage());
        }
        return OFFER + "#offer-video";
    }

    @PostMapping("/feedback")
    public String addFeedback(@RequestParam String name, @RequestParam String school, @RequestParam String district,
            @RequestParam String comment, RedirectAttributes redirect) {
        try {
            var input = new FeedbackInput(name, school, district, "", comment);
            content.addFeedback(input, Status.PUBLISHED);
            redirect.addFlashAttribute("feedbackSuccess", "Student feedback added.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("feedbackError", e.getMessage());
        }
        return FEEDBACK;
    }

    @GetMapping("/feedback/template")
    public ResponseEntity<byte[]> feedbackTemplate() throws IOException {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=student-feedback.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(PurchaseWorkbook.template("Student Feedback", FEEDBACK_HEADERS));
    }

    @PostMapping("/feedback/bulk")
    public String importFeedback(@RequestParam MultipartFile workbook,
            @RequestParam(defaultValue = "false") boolean consent,
            @RequestParam(defaultValue = "false") boolean published, RedirectAttributes redirect) {
        try {
            requireConsent(consent);
            var rows = PurchaseWorkbook.read(workbook, FEEDBACK_HEADERS, LEGACY_FEEDBACK_HEADERS, 250, "feedback entry").stream()
                .map(row -> {
                    var values = row.values();
                    return new FeedbackImportRow(row.number(),
                        new FeedbackInput(values.get(0), values.get(1), values.get(2), "", values.getLast()));
                }).toList();
            int count = content.importFeedback(rows, published ? Status.PUBLISHED : Status.PENDING);
            redirect.addFlashAttribute("feedbackSuccess", count + " feedback entries imported.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("feedbackError", e.getMessage() + " No feedback was imported.");
        }
        return FEEDBACK;
    }

    @PostMapping("/feedback/{id}/status")
    public String moderate(@PathVariable Long id, @RequestParam Status status, RedirectAttributes redirect) {
        content.moderate(id, status);
        redirect.addFlashAttribute("feedbackSuccess", status == Status.PUBLISHED ? "Feedback published on the offer page."
            : status == Status.HIDDEN ? "Feedback hidden." : "Feedback moved to the review queue.");
        return FEEDBACK;
    }

    private static void requireConsent(boolean consent) {
        if (!consent) throw new IllegalArgumentException("Confirm you have permission to publish the student details and feedback.");
    }
}
