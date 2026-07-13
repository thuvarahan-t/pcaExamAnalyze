package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.service.McqAdminService;
import com.example.pcaExamAnalyze.service.McqStudentExamService;
import com.example.pcaExamAnalyze.web.dto.ExamStudentDetailsForm;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

/** Public entry point for the passwordless PCA MCQ Exam System. */
@Controller
public class ExamPortalController {

    private final McqAdminService mcqAdminService;
    private final McqStudentExamService studentExamService;

    public ExamPortalController(McqAdminService mcqAdminService, McqStudentExamService studentExamService) {
        this.mcqAdminService = mcqAdminService;
        this.studentExamService = studentExamService;
    }

    private static final String STUDENT_DETAILS_SESSION_KEY = "mcqStudentDetails";
    private static final String RESULT_LOOKUP_SESSION_KEY = "mcqResultLookup";

    private static final List<String> STREAMS = List.of(
            "Bio Science", "Physical Science");

    private static final List<String> DISTRICTS = List.of(
            "Colombo", "Gampaha", "Kalutara", "Kandy", "Matale", "Nuwara Eliya",
            "Galle", "Matara", "Hambantota", "Jaffna", "Kilinochchi", "Mannar",
            "Vavuniya", "Mullaitivu", "Batticaloa", "Ampara", "Trincomalee",
            "Kurunegala", "Puttalam", "Anuradhapura", "Polonnaruwa", "Badulla",
            "Monaragala", "Ratnapura", "Kegalle");

    @GetMapping({"/exam", "/exam/"})
    public String examPortal(@RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "") String q,
                             @RequestParam(defaultValue = "") String batch,
                             @RequestParam(defaultValue = "") String month,
                             @RequestParam(required = false) Integer year,
                             @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                             Model model, HttpSession session) {
        if ("XMLHttpRequest".equals(requestedWith)) {
            addExamResultsModel(model, page, q, batch, month, year);
            return "exam/index :: examResults";
        }
        addExamPortalModel(model, session, page, q, batch, month, year);
        return "exam/index";
    }

    @GetMapping("/exam/p/{slug}")
    public String sharedExam(@PathVariable String slug, Model model, HttpSession session) {
        var exam = studentExamService.examDetails(slug);
        ExamStudentDetailsForm details = savedDetails(session);
        model.addAttribute("exam", exam);
        model.addAttribute("studentDetails", details);
        model.addAttribute("hasSavedStudentDetails", details != null);
        model.addAttribute("savedStudentName", details == null ? null : details.getFullName());
        model.addAttribute("alreadySubmitted", studentExamService.alreadySubmitted(exam.id(), details));
        return "exam/details";
    }

    @GetMapping("/exam/details")
    public String studentDetails(@RequestParam(defaultValue = "") String returnTo,
                                 Model model, HttpSession session) {
        if (!model.containsAttribute("studentDetails")) {
            ExamStudentDetailsForm saved = savedDetails(session);
            model.addAttribute("studentDetails", saved == null ? new ExamStudentDetailsForm() : saved);
        }
        addExamPortalModel(model, session);
        model.addAttribute("detailsModal", true);
        model.addAttribute("detailsReturnTo", safeReturnTo(returnTo));
        return "exam/index";
    }

    @GetMapping("/exam/admin-login")
    public String adminLogin(Model model, HttpSession session, Authentication authentication) {
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_TEACHER"))) {
            return "redirect:/exam/admin";
        }
        addExamPortalModel(model, session);
        model.addAttribute("adminLoginModal", true);
        return "exam/index";
    }

    @GetMapping("/exam/result")
    public String myResult(Model model, HttpSession session) {
        addExamPortalModel(model, session);
        ExamStudentDetailsForm details = savedDetails(session);
        ResultLookup lookup = savedResultLookup(session);
        model.addAttribute("resultRegistrationId", details != null ? details.getRegistrationId()
                : lookup == null ? "" : lookup.registrationId());
        model.addAttribute("resultNic", details != null ? details.getNic() : lookup == null ? "" : lookup.nic());
        model.addAttribute("resultModal", true);
        return "exam/index";
    }

    @PostMapping("/exam/result")
    public String openMyResults(@RequestParam String registrationId,
                                @RequestParam String nic,
                                Model model, HttpSession session) {
        String normalizedRegistration = registrationId == null ? ""
                : registrationId.trim().toUpperCase(Locale.ROOT);
        String normalizedNic = nic == null ? "" : nic.replaceAll("\\D", "");
        if (normalizedRegistration.isBlank() || normalizedRegistration.length() > 40
                || !normalizedNic.matches("[0-9]{12}")) {
            addExamPortalModel(model, session);
            model.addAttribute("resultModal", true);
            model.addAttribute("resultLookupError", "Enter a valid Registration ID and 12 digit NIC number");
            model.addAttribute("resultRegistrationId", normalizedRegistration);
            model.addAttribute("resultNic", normalizedNic);
            return "exam/index";
        }
        session.setAttribute(RESULT_LOOKUP_SESSION_KEY, new ResultLookup(normalizedRegistration, normalizedNic));
        return "redirect:/exam/results";
    }

    @GetMapping("/exam/results")
    public String myResultsDashboard(Model model, HttpSession session) {
        ResultLookup lookup = savedResultLookup(session);
        if (lookup == null) return "redirect:/exam/result";
        model.addAttribute("dashboard", studentExamService.resultsDashboard(lookup.registrationId(), lookup.nic()));
        return "exam/results";
    }

    @GetMapping("/exam/results/{submissionId}")
    public String myResultDetail(@PathVariable Long submissionId, Model model, HttpSession session,
                                 RedirectAttributes redirect) {
        ResultLookup lookup = savedResultLookup(session);
        if (lookup == null) return "redirect:/exam/result";
        try {
            model.addAttribute("result", studentExamService.resultBySubmission(
                    submissionId, lookup.registrationId(), lookup.nic()));
            return "exam/result-detail";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("examError", ex.getMessage());
            return "redirect:/exam/results";
        }
    }

    @PostMapping("/exam/details")
    public String saveStudentDetails(
            @Valid @ModelAttribute("studentDetails") ExamStudentDetailsForm form,
            BindingResult binding,
            @RequestParam(defaultValue = "") String returnTo,
            Model model,
            HttpSession session) {
        form.normalize();
        validateManagedChoice("batch", form.getBatch(), mcqAdminService.activeBatchNames(), binding);
        validateManagedChoice("stream", form.getStream(), STREAMS, binding);
        validateManagedChoice("district", form.getDistrict(), DISTRICTS, binding);
        if (binding.hasErrors()) {
            addExamPortalModel(model, session);
            model.addAttribute("detailsModal", true);
            model.addAttribute("detailsReturnTo", safeReturnTo(returnTo));
            return "exam/index";
        }
        session.setAttribute(STUDENT_DETAILS_SESSION_KEY, form);
        return "redirect:" + safeReturnTo(returnTo);
    }

    @PostMapping("/exam/p/{slug}/start")
    public String startExam(@PathVariable String slug, HttpSession session, RedirectAttributes redirect) {
        ExamStudentDetailsForm details = savedDetails(session);
        if (details == null) return "redirect:/exam/details?returnTo=/exam/p/" + slug;
        try {
            var submission = studentExamService.startOrResume(slug, details);
            if (submission.getStatus() == com.example.pcaExamAnalyze.domain.McqSubmissionStatus.SUBMITTED) {
                return "redirect:/exam/p/" + slug + "/result";
            }
            return "redirect:/exam/session/" + submission.getId();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("examError", ex.getMessage());
            return "redirect:/exam/p/" + slug;
        }
    }

    @GetMapping("/exam/session/{submissionId}")
    public String examWorkspace(@PathVariable Long submissionId, Model model, HttpSession session,
                                RedirectAttributes redirect) {
        try {
            var workspace = studentExamService.workspace(submissionId, savedDetails(session));
            if ("SUBMITTED".equals(workspace.status())) return "redirect:/exam/p/" + workspace.slug() + "/result";
            model.addAttribute("workspace", workspace);
            model.addAttribute("questionNumbers", IntStream.rangeClosed(1, workspace.totalQuestions()).boxed().toList());
            model.addAttribute("optionNumbers", IntStream.rangeClosed(1, workspace.optionsPerQuestion()).boxed().toList());
            return "exam/session";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("examError", ex.getMessage());
            return "redirect:/exam";
        }
    }

    @PostMapping("/exam/session/{submissionId}/answer")
    public ResponseEntity<?> saveAnswer(@PathVariable Long submissionId,
                                        @RequestParam int question,
                                        @RequestParam(required = false) Integer option,
                                        HttpSession session) {
        try {
            return ResponseEntity.ok(studentExamService.saveAnswer(submissionId, question, option, savedDetails(session)));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/exam/session/{submissionId}/answers")
    public ResponseEntity<?> saveAnswers(@PathVariable Long submissionId,
                                         @RequestParam Map<String, String> parameters,
                                         HttpSession session) {
        try {
            Map<Integer, Integer> answers = new java.util.LinkedHashMap<>();
            parameters.forEach((key, value) -> {
                if (key.startsWith("q")) {
                    answers.put(Integer.parseInt(key.substring(1)), Integer.parseInt(value));
                }
            });
            return ResponseEntity.ok(studentExamService.saveAnswers(submissionId, answers, savedDetails(session)));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/exam/session/{submissionId}/submit")
    public String submitExam(@PathVariable Long submissionId, HttpSession session, RedirectAttributes redirect) {
        try {
            var result = studentExamService.submit(submissionId, savedDetails(session));
            return "redirect:/exam/p/" + result.slug() + "/result";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("examError", ex.getMessage());
            return "redirect:/exam/session/" + submissionId;
        }
    }

    @GetMapping("/exam/p/{slug}/result")
    public String examResult(@PathVariable String slug, Model model, HttpSession session,
                             RedirectAttributes redirect) {
        try {
            model.addAttribute("result", studentExamService.result(slug, savedDetails(session)));
            return "exam/result";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("examError", ex.getMessage());
            return "redirect:/exam/p/" + slug;
        }
    }

    @GetMapping("/exam/p/{slug}/result-lookup")
    public String resultLookup(@PathVariable String slug, Model model) {
        model.addAttribute("exam", studentExamService.examDetails(slug));
        return "exam/result-lookup";
    }

    @PostMapping("/exam/p/{slug}/result-lookup")
    public String findResult(@PathVariable String slug,
                             @RequestParam String registrationId,
                             @RequestParam String nic,
                             Model model) {
        try {
            model.addAttribute("result", studentExamService.result(slug, registrationId, nic));
            return "exam/result";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("exam", studentExamService.examDetails(slug));
            model.addAttribute("lookupError", ex.getMessage());
            model.addAttribute("registrationId", registrationId == null ? "" : registrationId.trim());
            return "exam/result-lookup";
        }
    }

    @PostMapping("/exam/details/clear")
    public String clearStudentDetails(HttpSession session) {
        session.removeAttribute(STUDENT_DETAILS_SESSION_KEY);
        session.removeAttribute(RESULT_LOOKUP_SESSION_KEY);
        return "redirect:/exam";
    }

    private void addExamPortalModel(Model model, HttpSession session) {
        addExamPortalModel(model, session, 0, "", "", "", null);
    }

    private void addExamPortalModel(Model model, HttpSession session, int page, String search,
                                    String batch, String month, Integer year) {
        addExamResultsModel(model, page, search, batch, month, year);
        model.addAttribute("examFilterYears", mcqAdminService.publicExamYears());
        model.addAttribute("examFilterMonths", McqAdminService.MONTHS);
        ExamStudentDetailsForm details = savedDetails(session);
        model.addAttribute("hasSavedStudentDetails", details != null);
        model.addAttribute("savedStudentName", details == null ? null : details.getFullName());
        addDetailsOptions(model);
    }

    private void addExamResultsModel(Model model, int page, String search,
                                     String batch, String month, Integer year) {
        var examPage = mcqAdminService.publicExamPage(page, search, batch, month, year);
        model.addAttribute("examPage", examPage);
        model.addAttribute("exams", examPage.items());
        model.addAttribute("examSearch", search);
        model.addAttribute("selectedExamBatch", batch);
        model.addAttribute("selectedExamMonth", month);
        model.addAttribute("selectedExamYear", year);
    }

    private void addDetailsOptions(Model model) {
        model.addAttribute("examBatches", mcqAdminService.activeBatchNames());
        model.addAttribute("examStreams", STREAMS);
        model.addAttribute("examDistricts", DISTRICTS);
    }

    private static void validateManagedChoice(
            String field, String value, List<String> allowed, BindingResult binding) {
        if (value != null && !value.isBlank() && !allowed.contains(value)) {
            binding.rejectValue(field, "invalid", "Select a valid option");
        }
    }

    private static ExamStudentDetailsForm savedDetails(HttpSession session) {
        Object value = session.getAttribute(STUDENT_DETAILS_SESSION_KEY);
        return value instanceof ExamStudentDetailsForm details ? details : null;
    }

    private static ResultLookup savedResultLookup(HttpSession session) {
        Object value = session.getAttribute(RESULT_LOOKUP_SESSION_KEY);
        return value instanceof ResultLookup lookup ? lookup : null;
    }

    private static String safeReturnTo(String returnTo) {
        return returnTo != null && returnTo.matches("^/exam(?:/p/[A-Za-z0-9-]+)?$") ? returnTo : "/exam";
    }


    private record ResultLookup(String registrationId, String nic) implements java.io.Serializable {}
}
