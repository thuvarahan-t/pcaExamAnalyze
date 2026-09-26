package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.service.McqAdminService;
import com.example.pcaExamAnalyze.service.SyllabusService;
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

import com.example.pcaExamAnalyze.domain.McqExamQuestion;
import com.example.pcaExamAnalyze.service.McqExamQuestionService;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.CacheControl;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.time.LocalDate;
import java.util.stream.IntStream;

@Controller
@RequestMapping("/exam/admin")
public class McqAdminController {

    private static final List<Integer> QUESTION_NUMBERS = IntStream.rangeClosed(1, 50).boxed().toList();
    private static final List<Integer> OPTION_NUMBERS = IntStream.rangeClosed(1, 5).boxed().toList();

    private final McqAdminService admin;
    private final McqExamQuestionService questionImages;
    private final SyllabusService syllabusService;

    public McqAdminController(McqAdminService admin, McqExamQuestionService questionImages,
                              SyllabusService syllabusService) {
        this.questionImages = questionImages;
        this.admin = admin;
        this.syllabusService = syllabusService;
    }

    @ModelAttribute
    public void commonModel(Model model) {
        model.addAttribute("adminBatches", admin.activeBatchNames());
        model.addAttribute("adminMonths", McqAdminService.MONTHS);
        model.addAttribute("adminYears", IntStream.rangeClosed(LocalDateTime.now().getYear(), LocalDateTime.now().getYear() + 5).boxed().toList());
        model.addAttribute("questionNumbers", QUESTION_NUMBERS);
        model.addAttribute("optionNumbers", OPTION_NUMBERS);
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
        var result = admin.save(null, form, "publish".equals(action), authentication.getName());
        if (result.exam().usesQuestionImages()) {
            // New image-sheet exams go straight to the question editor to add their questions.
            redirect.addFlashAttribute("success", "Exam saved. Now add each question's image and details.");
            return "redirect:/exam/admin/exams/" + result.exam().getId() + "/questions";
        }
        redirect.addFlashAttribute("success", result.published() ? "Exam scheduled successfully" : "Exam saved as draft");
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
        boolean publish = "publish".equals(action);
        var result = admin.save(id, form, publish, authentication.getName());
        if (publish && !result.published()) {
            redirect.addFlashAttribute("warning", missingImagesMessage(result.missingImages()));
            return "redirect:/exam/admin/exams/" + id + "/questions";
        }
        redirect.addFlashAttribute("success", result.published() ? "Exam updated and scheduled" : "Draft saved successfully");
        return "redirect:/exam/admin/exams";
    }

    /** Syllabus reference: one table per unit (Competency, Level, Content, Learning outcomes, Periods). */
    @GetMapping("/syllabus")
    public String syllabus(Model model) {
        model.addAttribute("adminPage", "syllabus");
        model.addAttribute("syllabusUnits", syllabusService.list());
        return "exam/admin/portal";
    }

    @GetMapping("/exams/{id}/questions")
    public String questionEditor(@PathVariable Long id, Model model) {
        model.addAttribute("adminPage", "exam-questions");
        model.addAttribute("editor", admin.questionEditor(id));
        model.addAttribute("timeOptions", McqExamQuestion.TIME_OPTIONS);
        return "exam/admin/portal";
    }

    @PostMapping("/exams/{id}/questions/{question}")
    @ResponseBody
    public ResponseEntity<?> saveQuestion(@PathVariable Long id, @PathVariable int question,
                                          @RequestParam(name = "correct", required = false) List<Integer> correct,
                                          @RequestParam(required = false) Double weight,
                                          @RequestParam(required = false) Integer timeSeconds,
                                          @RequestParam(required = false) MultipartFile image,
                                          @RequestParam(defaultValue = "false") boolean removeImage,
                                          @RequestParam(defaultValue = "false") boolean freeMark,
                                          @RequestParam(required = false) List<MultipartFile> subImages,
                                          @RequestParam(required = false) List<Long> removeSubImages,
                                          jakarta.servlet.http.HttpServletRequest request) {
        try {
            List<Integer> options = correct == null ? List.of()
                    : correct.stream().filter(o -> o != null && o >= 1 && o <= 5).distinct().sorted().toList();
            var saved = admin.saveEditorQuestion(id, question, options,
                    new McqExamQuestionService.QuestionEdit(weight, timeSeconds, valuesOrNull(request, "unit"),
                            valuesOrNull(request, "competencyLevel"), valuesOrNull(request, "content"),
                            valuesOrNull(request, "learningOutcome"),
                            tagInputs(request)),
                    image, removeImage, freeMark, subImages, removeSubImages);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/exams/{id}/schedule")
    public String schedule(@PathVariable Long id, RedirectAttributes redirect) {
        List<Integer> missing = admin.schedule(id);
        if (!missing.isEmpty()) {
            redirect.addFlashAttribute("warning", missingImagesMessage(missing));
            return "redirect:/exam/admin/exams/" + id + "/questions";
        }
        redirect.addFlashAttribute("success", "Exam scheduled successfully");
        return "redirect:/exam/admin/exams";
    }

    /** Every question's syllabus tags, for the Question Editor. */
    @GetMapping("/exams/{id}/questions/{question}/sub-images/{imageId}")
    public ResponseEntity<byte[]> subImage(@PathVariable Long id, @PathVariable int question, @PathVariable Long imageId) {
        return questionImages.subImage(id, question, imageId)
                .map(img -> QuestionImageResponses.of(img, questionImages.subImageRedirectUrl(img)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/exams/{id}/questions/tags")
    @ResponseBody
    public Map<Integer, List<McqExamQuestionService.TagView>> questionTags(@PathVariable Long id) {
        return admin.questionTagsByExam(id);
    }

    /**
     * Syllabus blocks from the editor, sent as parallel repeated fields: tagUnit, tagLevel,
     * tagContents (newline-separated tree paths) and tagOutcomes (newline-separated).
     * Returns null when the form did not include tags at all (leave existing tags untouched).
     */
    private static List<McqExamQuestionService.TagInput> tagInputs(jakarta.servlet.http.HttpServletRequest request) {
        if (request.getParameter("tagsSent") == null) return null;
        List<String> units = values(request, "tagUnit");
        List<String> levels = values(request, "tagLevel");
        List<String> contents = values(request, "tagContents");
        List<String> outcomes = values(request, "tagOutcomes");
        List<McqExamQuestionService.TagInput> result = new java.util.ArrayList<>();
        for (int i = 0; i < units.size(); i++) {
            Long unit = parseId(units.get(i));
            if (unit == null) continue;
            result.add(new McqExamQuestionService.TagInput(unit, i < levels.size() ? parseId(levels.get(i)) : null,
                    lines(i < contents.size() ? contents.get(i) : ""), lines(i < outcomes.size() ? outcomes.get(i) : "")));
        }
        return result;
    }

    /** Like {@link #values} but null when the field was not sent at all. */
    private static List<String> valuesOrNull(jakarta.servlet.http.HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        return values == null ? null : List.of(values);
    }

    private static Long parseId(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<String> lines(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split("\\r?\\n"));
    }

    /**
     * Repeated form fields read raw: binding a single value to List<String> would split it on
     * commas, which breaks entries such as "Force, mass and acceleration".
     */
    private static List<String> values(jakarta.servlet.http.HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        return values == null ? List.of() : List.of(values);
    }

    /** Final "Save & Finish" in the Question Editor: optionally schedule, then back to Manage Exams. */
    @PostMapping("/exams/{id}/questions/finish")
    public String finishQuestions(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean schedule,
                                  RedirectAttributes redirect) {
        if (!schedule) {
            redirect.addFlashAttribute("success", "All questions saved. The exam is kept as a draft.");
            return "redirect:/exam/admin/exams";
        }
        List<Integer> missing = admin.schedule(id);
        if (!missing.isEmpty()) {
            redirect.addFlashAttribute("warning", missingImagesMessage(missing));
            return "redirect:/exam/admin/exams/" + id + "/questions";
        }
        redirect.addFlashAttribute("success", "All questions saved and the exam is scheduled.");
        return "redirect:/exam/admin/exams";
    }

    private static String missingImagesMessage(List<Integer> missing) {
        return "Everything is saved, but the exam stays a draft until every question has an image. Upload: "
                + missing.stream().map(q -> "Q" + q).collect(java.util.stream.Collectors.joining(", "));
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

    @GetMapping("/exams/{id}/questions/{question}/image")
    public ResponseEntity<byte[]> questionImage(@PathVariable Long id, @PathVariable int question) {
        return admin.questionImage(id, question)
                .map(image -> QuestionImageResponses.of(image, questionImages.imageRedirectUrl(image)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void showForm(Model model, Long id) {
        model.addAttribute("adminPage", "exam-form");
        model.addAttribute("missingQuestionImages", id == null ? List.of() : admin.missingQuestionImages(id));
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
