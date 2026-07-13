package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.service.McqAdminService;
import com.example.pcaExamAnalyze.web.dto.McqExamForm;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.stream.IntStream;

@Controller
@RequestMapping("/exam/admin")
public class McqAdminController {

    private final McqAdminService admin;

    public McqAdminController(McqAdminService admin) {
        this.admin = admin;
    }

    @ModelAttribute
    public void commonModel(Model model) {
        model.addAttribute("adminBatches", admin.activeBatchNames());
        model.addAttribute("adminMonths", McqAdminService.MONTHS);
        model.addAttribute("adminYears", IntStream.rangeClosed(LocalDateTime.now().getYear(), LocalDateTime.now().getYear() + 5).boxed().toList());
        model.addAttribute("questionNumbers", admin.questionNumbers());
        model.addAttribute("optionNumbers", admin.optionNumbers());
    }

    @GetMapping({"", "/"})
    public String dashboard(@RequestParam(defaultValue = "") String popup, Model model) {
        model.addAttribute("adminPage", "dashboard");
        model.addAttribute("dashboard", admin.dashboard());
        model.addAttribute("batchRows", admin.batchRows());
        model.addAttribute("dashboardPopup", popup);
        return "exam/admin/portal";
    }

    @PostMapping("/batches")
    public String addBatch(@RequestParam String name, RedirectAttributes redirect) {
        try {
            admin.addBatch(name);
            redirect.addFlashAttribute("success", "Batch added successfully");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/exam/admin?popup=batches";
    }

    @PostMapping("/batches/{id}/toggle")
    public String toggleBatch(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            admin.toggleBatch(id);
            redirect.addFlashAttribute("success", "Batch status updated");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/exam/admin?popup=batches";
    }

    @PostMapping("/batches/{id}/rename")
    public String renameBatch(@PathVariable Long id, @RequestParam String name, RedirectAttributes redirect) {
        try {
            admin.renameBatch(id, name);
            redirect.addFlashAttribute("success", "Batch renamed successfully");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/exam/admin?popup=batches";
    }

    @PostMapping("/batches/{id}/delete")
    public String deleteBatch(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            admin.deleteBatch(id);
            redirect.addFlashAttribute("success", "Batch deleted");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/exam/admin?popup=batches";
    }

    @GetMapping("/exams")
    public String exams(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "") String q,
                        @RequestParam(defaultValue = "") String batch,
                        @RequestParam(defaultValue = "") String month,
                        @RequestParam(required = false) Integer year,
                        Model model) {
        model.addAttribute("adminPage", "exams");
        var examPage = admin.examPage(page, q, batch, month, year);
        model.addAttribute("examPage", examPage);
        model.addAttribute("examRows", examPage.items());
        model.addAttribute("search", q);
        model.addAttribute("selectedBatch", batch);
        model.addAttribute("selectedMonth", month);
        model.addAttribute("selectedYear", year);
        return "exam/admin/portal";
    }

    @GetMapping("/exams/{id}/analytics")
    public String examAnalytics(@PathVariable Long id, Model model) {
        model.addAttribute("adminPage", "exam-analytics");
        model.addAttribute("analytics", admin.examAnalytics(id));
        return "exam/admin/portal";
    }

    @GetMapping("/submissions/{id}")
    public String submissionDetail(@PathVariable Long id, Model model) {
        model.addAttribute("adminPage", "submission-detail");
        model.addAttribute("submissionDetail", admin.submissionDetail(id));
        return "exam/admin/portal";
    }

    @PostMapping("/submissions/{id}/delete")
    public String deleteSubmission(@PathVariable Long id, RedirectAttributes redirect) {
        admin.deleteSubmission(id);
        redirect.addFlashAttribute("success", "Student submission deleted successfully");
        return "redirect:/exam/admin/submissions";
    }

    @GetMapping("/exams/new")
    public String newExam(Model model) {
        if (!model.containsAttribute("examForm")) {
            model.addAttribute("examForm", new McqExamForm());
        }
        showForm(model, null);
        return "exam/admin/portal";
    }

    @PostMapping("/exams")
    public String createExam(@Valid @ModelAttribute("examForm") McqExamForm form,
                             BindingResult binding,
                             @RequestParam(defaultValue = "draft") String action,
                             Authentication authentication,
                             Model model,
                             RedirectAttributes redirect) {
        admin.validateForm(form, binding);
        if (binding.hasErrors()) {
            showForm(model, null);
            return "exam/admin/portal";
        }
        admin.save(null, form, "publish".equals(action), authentication.getName());
        redirect.addFlashAttribute("success", "publish".equals(action)
                ? "Exam scheduled successfully" : "Exam saved as draft");
        return "redirect:/exam/admin/exams";
    }

    @GetMapping("/exams/{id}/edit")
    public String editExam(@PathVariable Long id, Model model) {
        if (!model.containsAttribute("examForm")) model.addAttribute("examForm", admin.formFor(id));
        showForm(model, id);
        return "exam/admin/portal";
    }

    @PostMapping("/exams/{id}/update")
    public String updateExam(@PathVariable Long id,
                             @Valid @ModelAttribute("examForm") McqExamForm form,
                             BindingResult binding,
                             @RequestParam(defaultValue = "draft") String action,
                             Authentication authentication,
                             Model model,
                             RedirectAttributes redirect) {
        admin.validateForm(form, binding);
        if (binding.hasErrors()) {
            showForm(model, id);
            return "exam/admin/portal";
        }
        admin.save(id, form, "publish".equals(action), authentication.getName());
        redirect.addFlashAttribute("success", "publish".equals(action)
                ? "Exam updated and scheduled" : "Draft saved successfully");
        return "redirect:/exam/admin/exams";
    }

    @PostMapping("/exams/{id}/archive")
    public String archive(@PathVariable Long id, RedirectAttributes redirect) {
        admin.archive(id);
        redirect.addFlashAttribute("success", "Exam archived");
        return "redirect:/exam/admin/exams";
    }

    @PostMapping("/exams/{id}/delete")
    public String deleteExam(@PathVariable Long id, RedirectAttributes redirect) {
        admin.deleteExam(id);
        redirect.addFlashAttribute("success", "Exam and all related submissions deleted successfully");
        return "redirect:/exam/admin/exams";
    }

    @PostMapping("/exams/{id}/release-results")
    public String releaseResults(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            admin.releaseResults(id);
            redirect.addFlashAttribute("success", "Results released successfully");
        } catch (IllegalStateException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/exam/admin/exams";
    }

    @PostMapping("/exams/{id}/unpublish-results")
    public String unpublishResults(@PathVariable Long id, RedirectAttributes redirect) {
        admin.unpublishResults(id);
        redirect.addFlashAttribute("success", "Results unpublished");
        return "redirect:/exam/admin/exams";
    }

    @GetMapping("/submissions")
    public String submissions(@RequestParam(required = false) Long examId,
                              @RequestParam(defaultValue = "") String q,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                              Model model) {
        model.addAttribute("adminPage", "submissions");
        model.addAttribute("examRows", admin.examRows());
        var submissionPage = admin.submissionPage(examId, q, from, to, page);
        model.addAttribute("submissionPage", submissionPage);
        model.addAttribute("submissionRows", submissionPage.items());
        model.addAttribute("selectedExamId", examId);
        model.addAttribute("search", q);
        model.addAttribute("dateFrom", from);
        model.addAttribute("dateTo", to);
        return "exam/admin/portal";
    }

    @GetMapping("/exams/{id}/export.csv")
    public ResponseEntity<byte[]> exportExam(@PathVariable Long id) {
        return csv(admin.exportCsv(id), "pca-exam-" + id + "-submissions.csv");
    }

    @GetMapping("/submissions/export.csv")
    public ResponseEntity<byte[]> exportAll(@RequestParam(required = false) Long examId) {
        return csv(admin.exportCsv(examId), "pca-mcq-submissions.csv");
    }

    private void showForm(Model model, Long id) {
        model.addAttribute("adminPage", "exam-form");
        model.addAttribute("editingExamId", id);
        model.addAttribute("editingExamSlug", id == null ? null : admin.requireExam(id).getSlug());
        model.addAttribute("formTitle", id == null ? "Add Exam" : "Edit Exam");
    }

    private static ResponseEntity<byte[]> csv(byte[] content, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
