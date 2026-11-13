package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.service.ClassPackageService;
import com.example.pcaExamAnalyze.web.dto.*;
import jakarta.validation.Valid;
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
    public ClassPackageAdminController(ClassPackageService service) { this.service = service; }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public String uploadTooLarge(RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", "Poster is too large. Upload a JPEG or PNG file, 5 MB or smaller.");
        return "redirect:/exam/admin/packages";
    }
    @GetMapping
    public String index(Model model) {
        if (!model.containsAttribute("campaignForm")) model.addAttribute("campaignForm", service.campaignForm());
        model.addAttribute("packages", service.cards(true)); return "exam/admin/packages";
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
        model.addAttribute("pack", service.get(id)); model.addAttribute("purchases", service.purchaseList(id));
        model.addAttribute("districts", com.example.pcaExamAnalyze.report.ExamReportService.DISTRICTS);
        model.addAttribute("zone", ClassPackageService.ZONE); return "exam/admin/package-purchases";
    }
    @PostMapping("/{id}/purchases")
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
    @PostMapping("/{id}/purchases/{purchaseId}/remove")
    public String removePurchase(@PathVariable Long id, @PathVariable Long purchaseId) {
        service.removePurchase(id, purchaseId);
        return "redirect:/exam/admin/packages/" + id + "/purchases";
    }
}
