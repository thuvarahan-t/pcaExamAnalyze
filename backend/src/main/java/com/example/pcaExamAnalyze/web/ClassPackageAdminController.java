package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.domain.OfferFeedback.Status;
import com.example.pcaExamAnalyze.service.ClassPackageService;
import com.example.pcaExamAnalyze.service.OfferContentService;
import com.example.pcaExamAnalyze.report.ExamReportService;
import com.example.pcaExamAnalyze.web.dto.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/exam/admin/packages")
public class ClassPackageAdminController {
    private final ClassPackageService service;
    private final OfferContentService content;
    public ClassPackageAdminController(ClassPackageService service, OfferContentService content) {
        this.service = service;
        this.content = content;
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public String uploadTooLarge(RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", "Poster is too large. Upload a JPEG or PNG file, 5 MB or smaller.");
        return "redirect:/exam/admin/packages";
    }
    @GetMapping
    public String index(Model model) {
        if (!model.containsAttribute("campaignForm")) model.addAttribute("campaignForm", service.campaignForm());
        model.addAttribute("packages", service.cards(true));
        model.addAttribute("events", content.events(true));
        model.addAttribute("maxEventPhotos", OfferContentService.MAX_EVENT_PHOTOS);
        var feedbackByStatus = content.feedback(true).stream().collect(Collectors.groupingBy(entry -> entry.getStatus()));
        model.addAttribute("pendingFeedback", feedbackByStatus.getOrDefault(Status.PENDING, List.of()));
        model.addAttribute("publishedFeedback", feedbackByStatus.getOrDefault(Status.PUBLISHED, List.of()));
        model.addAttribute("hiddenFeedback", feedbackByStatus.getOrDefault(Status.HIDDEN, List.of()));
        model.addAttribute("districts", ExamReportService.DISTRICTS);
        var campaign = service.campaign();
        model.addAttribute("promoVideo", campaign.getMuxPlaybackId() == null ? campaign.getYoutubeVideoId()
            : "https://player.mux.com/" + campaign.getMuxPlaybackId());
        return "exam/admin/packages";
    }
    @PostMapping("/campaign")
    public String campaign(@Valid @ModelAttribute("campaignForm") PackageCampaignForm form, BindingResult errors,
            Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) return index(model);
        service.saveCampaign(form); redirect.addFlashAttribute("success", "Campaign settings updated."); return "redirect:/exam/admin/packages";
    }
    @GetMapping("/new")
    public String newPackage(Model model) { model.addAttribute("packageForm", new ClassPackageForm()); return editor(null, model); }
    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) { model.addAttribute("packageForm", service.form(id)); return editor(id, model); }
    private String editor(Long id, Model model) { model.addAttribute("packageId", id); return "exam/admin/package-edit"; }
    @PostMapping({"/new", "/{id}/edit"})
    public String save(@PathVariable(required = false) Long id, @Valid @ModelAttribute("packageForm") ClassPackageForm form,
            BindingResult errors, @RequestParam(required = false) MultipartFile poster, Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) return editor(id, model);
        try {
            service.save(id, form, poster); redirect.addFlashAttribute("success", "Package saved."); return "redirect:/exam/admin/packages";
        } catch (IllegalArgumentException e) { model.addAttribute("error", e.getMessage()); return editor(id, model); }
    }
    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes redirect) {
        service.toggle(id); redirect.addFlashAttribute("success", "Package visibility updated."); return "redirect:/exam/admin/packages";
    }
    @GetMapping("/{id}/poster")
    public ResponseEntity<byte[]> poster(@PathVariable Long id) {
        var image = service.poster(id, true);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
            .contentType(MediaType.parseMediaType(image.getContentType())).body(image.getData());
    }
    @GetMapping("/{id}/purchases")
    public String purchases(@PathVariable Long id, Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        var pack = service.get(id);
        model.addAttribute("pack", pack);
        model.addAttribute("purchases", service.purchaseList(id));
        model.addAttribute("interested", service.interestedStudents(id));
        model.addAttribute("districts", com.example.pcaExamAnalyze.report.ExamReportService.DISTRICTS);
        model.addAttribute("zone", ClassPackageService.ZONE); return "exam/admin/package-purchases";
    }
    @GetMapping("/{id}/purchases/template")
    public ResponseEntity<byte[]> purchaseTemplate(@PathVariable Long id) throws java.io.IOException {
        service.get(id);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=confirmed-purchases.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(PurchaseWorkbook.template());
    }

    @PostMapping("/{id}/purchases/bulk")
    public String bulkPurchases(@PathVariable Long id, @RequestParam MultipartFile workbook, RedirectAttributes redirect) {
        try {
            int count = service.addBulkPurchases(id, PurchaseWorkbook.read(workbook));
            redirect.addFlashAttribute("success", count + " confirmed purchases added.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage() + " No purchases were added.");
        }
        return "redirect:/exam/admin/packages/" + id + "/purchases";
    }

    @PostMapping(path = "/{id}/purchases", params = {"registrationId", "!phoneSuffix"})
    public String addPurchase(@PathVariable Long id, @RequestParam String registrationId,
            @RequestParam(defaultValue = "") String name, @RequestParam(defaultValue = "") String school,
            @RequestParam(defaultValue = "") String district, RedirectAttributes redirect) {
        try { service.addPurchase(id, registrationId, name, school, district); redirect.addFlashAttribute("success", "Confirmed purchase added. Student count updated."); }
        catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage()); redirect.addFlashAttribute("enteredRegistration", registrationId);
            redirect.addFlashAttribute("enteredName", name);
            redirect.addFlashAttribute("enteredSchool", school); redirect.addFlashAttribute("enteredDistrict", district);
        }
        return "redirect:/exam/admin/packages/" + id + "/purchases";
    }
    @PostMapping(path = "/{id}/purchases", params = "phoneSuffix")
    public String addContactPurchase(@PathVariable Long id, @RequestParam String firstName,
            @RequestParam(defaultValue = "") String registrationId, @RequestParam String phoneSuffix,
            @RequestParam(defaultValue = "") String school, @RequestParam(defaultValue = "") String district,
            @RequestParam(defaultValue = "false") boolean sharedPhone, RedirectAttributes redirect) {
        try {
            service.addContactPurchase(id, firstName, registrationId, "07" + phoneSuffix, school, district, sharedPhone);
            redirect.addFlashAttribute("success", "Confirmed purchase added. Student count updated.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            redirect.addFlashAttribute("enteredFirstName", firstName);
            redirect.addFlashAttribute("enteredRegistration", registrationId);
            redirect.addFlashAttribute("enteredPhone", phoneSuffix);
            redirect.addFlashAttribute("enteredSchool", school);
            redirect.addFlashAttribute("enteredDistrict", district);
        }
        return "redirect:/exam/admin/packages/" + id + "/purchases";
    }
    @PostMapping("/{id}/enquiries/{enquiryId}/confirm")
    public String confirmEnquiry(@PathVariable Long id, @PathVariable Long enquiryId, RedirectAttributes redirect) {
        try {
            service.confirmEnquiry(id, enquiryId);
            redirect.addFlashAttribute("success", "Enquiry purchase confirmed.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/exam/admin/packages/" + id + "/purchases#interestedStudents";
    }
    @PostMapping("/{id}/enquiries/{enquiryId}/name")
    public String editEnquiryName(@PathVariable Long id, @PathVariable Long enquiryId,
            @RequestParam String firstName, @RequestParam String lastName, RedirectAttributes redirect) {
        try {
            service.editEnquiryName(id, enquiryId, firstName, lastName);
            redirect.addFlashAttribute("success", "Student name updated.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/exam/admin/packages/" + id + "/purchases#interestedStudents";
    }
    @PostMapping("/{id}/purchases/{purchaseId}/remove")
    public String removePurchase(@PathVariable Long id, @PathVariable Long purchaseId) {
        service.removePurchase(id, purchaseId);
        return "redirect:/exam/admin/packages/" + id + "/purchases";
    }
}
