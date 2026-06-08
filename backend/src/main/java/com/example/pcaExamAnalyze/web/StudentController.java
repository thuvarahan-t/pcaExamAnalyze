package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.analysis.ReportData;
import com.example.pcaExamAnalyze.domain.User;
import com.example.pcaExamAnalyze.service.AttemptService;
import com.example.pcaExamAnalyze.service.StudentReportService;
import com.example.pcaExamAnalyze.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.*;

@Controller
@RequestMapping("/student")
public class StudentController {

    private final UserService userService;
    private final AttemptService attemptService;
    private final StudentReportService reportService;

    public StudentController(UserService userService, AttemptService attemptService,
                            StudentReportService reportService) {
        this.userService = userService;
        this.attemptService = attemptService;
        this.reportService = reportService;
    }

    private User current(Principal principal) {
        return userService.requireByUsername(principal.getName());
    }

    // ---------- Dashboard: attempt tabs + marking sheet ----------

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) Integer attempt,
                            Principal principal, Model model) {
        User student = current(principal);

        List<Integer> saved = attemptService.savedAttemptNumbers(student.getId());
        // Always show at least "Attempt 1" (virtual until the student saves it).
        List<Integer> tabs = saved.isEmpty() ? List.of(1) : saved;

        int selected = (attempt != null && tabs.contains(attempt)) ? attempt : tabs.get(tabs.size() - 1);
        AttemptService.SheetView sheet = attemptService.sheet(student.getId(), selected);

        model.addAttribute("studentName", student.getFullName());
        model.addAttribute("tabs", tabs);
        model.addAttribute("selected", selected);
        model.addAttribute("sheet", sheet);
        model.addAttribute("savedAttempts", saved);   // report dropdown: per-attempt options
        return "student/dashboard";
    }

    @PostMapping("/attempt/add")
    public String addAttempt(Principal principal, RedirectAttributes ra) {
        User student = current(principal);
        int n = attemptService.addAttempt(student);
        ra.addFlashAttribute("flashSuccess", "Attempt " + n + " added — enter your marks.");
        return "redirect:/student/dashboard?attempt=" + n;
    }

    @PostMapping("/attempt/{n}/save")
    public String saveSheet(@PathVariable int n,
                            @RequestParam Map<String, String> allParams,
                            Principal principal, RedirectAttributes ra) {
        User student = current(principal);
        Map<Long, Integer> marks = parseMarks(allParams);

        try {
            attemptService.saveSheet(student, n, marks);
            ra.addFlashAttribute("flashSuccess",
                    "Saved " + marks.size() + " mark" + (marks.size() == 1 ? "" : "s") + " for Attempt " + n + ".");
        } catch (ResponseStatusException ex) {
            ra.addFlashAttribute("flashError", ex.getReason());
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashError", "Could not save marks. Please try again.");
        }
        return "redirect:/student/dashboard?attempt=" + n;
    }

    @PostMapping("/attempt/{n}/autosave")
    @ResponseBody
    public Map<String, Object> autosaveSheet(@PathVariable int n,
                                             @RequestParam Map<String, String> allParams,
                                             Principal principal) {
        User student = current(principal);
        Map<Long, Integer> marks = parseMarks(allParams);

        try {
            attemptService.saveSheet(student, n, marks);
            return Map.of(
                    "ok", true,
                    "message", "Saved",
                    "count", marks.size());
        } catch (ResponseStatusException ex) {
            return Map.of(
                    "ok", false,
                    "message", ex.getReason() == null ? "Could not save marks." : ex.getReason());
        } catch (RuntimeException ex) {
            return Map.of(
                    "ok", false,
                    "message", "Could not save marks. Please try again.");
        }
    }

    private Map<Long, Integer> parseMarks(Map<String, String> allParams) {
        Map<Long, Integer> marks = new HashMap<>();
        for (var e : allParams.entrySet()) {
            if (!e.getKey().startsWith("mark_")) continue;
            String v = e.getValue();
            if (v == null || v.isBlank()) continue;          // blank = not answered
            try {
                Long qId = Long.parseLong(e.getKey().substring(5));
                marks.put(qId, Integer.parseInt(v.trim()));
            } catch (NumberFormatException ignored) { /* skip invalid */ }
        }
        return marks;
    }

    // ---------- Report ----------

    @GetMapping("/report")
    public String report(@RequestParam(required = false) Integer attempt,
                         @RequestParam(required = false) String scope,
                         Principal principal, Model model) {
        User student = current(principal);

        ReportData report;
        if (attempt != null && !"all".equalsIgnoreCase(scope)) {
            report = reportService.forAttempt(student.getId(), attempt);
        } else {
            report = reportService.forAllAttempts(student.getId());
        }

        model.addAttribute("studentName", student.getFullName());
        model.addAttribute("report", report);
        model.addAttribute("backAttempt", attempt);
        return "student/report";
    }
}
