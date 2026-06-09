package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.domain.PaperStructure;
import com.example.pcaExamAnalyze.domain.Question;
import com.example.pcaExamAnalyze.domain.ReferenceType;
import com.example.pcaExamAnalyze.domain.Section;
import com.example.pcaExamAnalyze.service.PaperService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/teacher")
public class TeacherController {

    private final PaperService paperService;

    public TeacherController(PaperService paperService) {
        this.paperService = paperService;
    }

    // ---------- Dashboard ----------

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<PaperService.GridRow> rows = paperService.teacherGrid();
        model.addAttribute("rows", rows);
        // Years (2021–2026) that don't yet have a paper, for the inline "add year" picker.
        java.util.Set<Integer> existing = rows.stream().map(PaperService.GridRow::year)
                .collect(java.util.stream.Collectors.toSet());
        model.addAttribute("availableYears", java.util.stream.IntStream.rangeClosed(2021, 2026)
                .filter(y -> !existing.contains(y)).boxed().toList());
        model.addAttribute("sections", paperService.allSections());
        model.addAttribute("refTypes", ReferenceType.values());
        return "teacher/dashboard";
    }

    // ---------- Papers ----------

    @GetMapping("/papers")
    public String papers(Model model) {
        model.addAttribute("summaries", paperService.paperSummaries());
        model.addAttribute("availableYears", List.of(2021, 2022, 2023, 2024, 2025));
        return "teacher/papers";
    }

    @PostMapping("/papers")
    public String createPaper(@RequestParam Integer year,
                              @RequestParam(required = false) String subject,
                              @RequestParam(required = false) String title,
                              RedirectAttributes ra) {
        String paperTitle = (title == null || title.isBlank())
                ? year + " A/L Physics Past Paper" : title;
        try {
            paperService.createPaper(year, subject, paperTitle);
            ra.addFlashAttribute("flashSuccess", "Paper for " + year + " created.");
        } catch (ResponseStatusException e) {
            ra.addFlashAttribute("flashError", e.getReason());
        }
        return "redirect:/teacher/papers";
    }

    /** Add a year straight from the question-map matrix (auto-titled), then back to the matrix. */
    @PostMapping("/years")
    public String addYear(@RequestParam Integer year, RedirectAttributes ra) {
        try {
            paperService.createPaper(year, null, year + " A/L Physics Past Paper");
            ra.addFlashAttribute("flashSuccess", "Year " + year + " added.");
        } catch (ResponseStatusException e) {
            ra.addFlashAttribute("flashError", e.getReason());
        }
        return "redirect:/teacher/dashboard";
    }

    @PostMapping("/papers/{id}/update")
    public String updatePaper(@PathVariable Long id,
                              @RequestParam Integer year,
                              @RequestParam(required = false) String subject,
                              @RequestParam String title,
                              RedirectAttributes ra) {
        try {
            paperService.updatePaper(id, year, subject, title);
            ra.addFlashAttribute("flashSuccess", "Paper updated.");
        } catch (ResponseStatusException e) {
            ra.addFlashAttribute("flashError", e.getReason());
        }
        return "redirect:/teacher/papers";
    }

    @PostMapping("/papers/{id}/delete")
    public String deletePaper(@PathVariable Long id, RedirectAttributes ra) {
        paperService.deletePaper(id);
        ra.addFlashAttribute("flashSuccess", "Paper deleted.");
        return "redirect:/teacher/papers";
    }

    /** AJAX: delete a whole year/paper (and its questions, resources, student marks) from the matrix. */
    @PostMapping(value = "/papers/{id}/delete", headers = "X-Requested-With=XMLHttpRequest")
    @ResponseBody
    public java.util.Map<String, Object> deletePaperAjax(@PathVariable Long id) {
        try {
            paperService.deletePaper(id);
            return java.util.Map.of("ok", true, "message", "Year deleted.");
        } catch (ResponseStatusException e) {
            return java.util.Map.of("ok", false,
                    "message", e.getReason() == null ? "Could not delete the year." : e.getReason());
        }
    }

    // ---------- Questions ----------

    @GetMapping("/papers/{paperId}/questions")
    public String questions(@PathVariable Long paperId, Model model) {
        PaperStructure paper = paperService.requirePaper(paperId);
        List<Question> questions = paperService.questionsOf(paperId);
        model.addAttribute("paper", paper);
        model.addAttribute("questions", questions);
        model.addAttribute("refCounts", questions.stream()
                .collect(java.util.stream.Collectors.toMap(Question::getId,
                        q -> (long) paperService.referencesOf(q.getId()).size())));
        model.addAttribute("sections", paperService.allSections());
        model.addAttribute("questionNumbers", List.of("Q1", "Q2", "Q3", "Q4"));
        model.addAttribute("defaultMarks", Question.DEFAULT_MAX_MARKS);
        return "teacher/questions";
    }

    @PostMapping("/papers/{paperId}/questions")
    public String addQuestion(@PathVariable Long paperId,
                              @RequestParam String questionNumber,
                              @RequestParam Long sectionId,
                              @RequestParam(required = false) Integer maxMarks,
                              @RequestParam(required = false) String description,
                              RedirectAttributes ra) {
        try {
            paperService.addQuestion(paperId, questionNumber, sectionId, maxMarks, description);
            ra.addFlashAttribute("flashSuccess", "Question added.");
        } catch (ResponseStatusException e) {
            ra.addFlashAttribute("flashError", e.getReason());
        }
        return "redirect:/teacher/papers/" + paperId + "/questions";
    }

    @PostMapping("/questions/{id}/update")
    public String updateQuestion(@PathVariable Long id,
                                 @RequestParam String questionNumber,
                                 @RequestParam Long sectionId,
                                 @RequestParam(required = false) Integer maxMarks,
                                 @RequestParam(required = false) String description,
                                 RedirectAttributes ra) {
        Long paperId = paperService.paperIdOfQuestion(id);
        try {
            paperService.updateQuestion(id, questionNumber, sectionId, maxMarks, description);
            ra.addFlashAttribute("flashSuccess", "Question updated.");
        } catch (ResponseStatusException e) {
            ra.addFlashAttribute("flashError", e.getReason());
        }
        return "redirect:/teacher/papers/" + paperId + "/questions";
    }

    // ---------- Sections (shared category list) ----------

    /** AJAX: create a section (or return the existing one) and hand back JSON for the live dropdown. */
    @PostMapping("/sections")
    @ResponseBody
    public Section createSection(@RequestParam String name) {
        return paperService.addSection(name);
    }

    @PostMapping("/sections/{id}/delete")
    public String deleteSection(@PathVariable Long id,
                                @RequestParam Long paperId,
                                RedirectAttributes ra) {
        try {
            paperService.deleteSection(id);
            ra.addFlashAttribute("flashSuccess", "Section deleted.");
        } catch (ResponseStatusException e) {
            ra.addFlashAttribute("flashError", e.getReason());
        }
        return "redirect:/teacher/papers/" + paperId + "/questions";
    }

    /** AJAX: rename a section from the live section dropdown. */
    @PostMapping("/sections/{id}/rename")
    @ResponseBody
    public Section renameSection(@PathVariable Long id, @RequestParam String name) {
        return paperService.renameSection(id, name);
    }

    /** AJAX: delete a section from the live section dropdown (409 with a message if still in use). */
    @PostMapping("/sections/{id}/remove")
    @ResponseBody
    public java.util.Map<String, Object> removeSection(@PathVariable Long id) {
        try {
            paperService.deleteSection(id);
            return java.util.Map.of("ok", true);
        } catch (ResponseStatusException e) {
            return java.util.Map.of("ok", false,
                    "message", e.getReason() == null ? "Could not delete the section." : e.getReason());
        }
    }

    @PostMapping("/questions/{id}/delete")
    public String deleteQuestion(@PathVariable Long id, RedirectAttributes ra) {
        Long paperId = paperService.deleteQuestion(id);
        ra.addFlashAttribute("flashSuccess", "Question deleted.");
        return "redirect:/teacher/papers/" + paperId + "/questions";
    }

    /** Assemble the popup's repeated resource rows into RefInput records (blank rows dropped). */
    private static List<PaperService.RefInput> buildRefInputs(List<String> refTitle, List<String> refType,
                                                              List<String> refResource, List<String> refNote) {
        List<PaperService.RefInput> rows = new java.util.ArrayList<>();
        if (refTitle == null) return rows;
        for (int i = 0; i < refTitle.size(); i++) {
            String t = refTitle.get(i);
            String res = (refResource != null && i < refResource.size()) ? refResource.get(i) : null;
            if (t == null || t.isBlank() || res == null || res.isBlank()) {
                continue;
            }
            ReferenceType type = ReferenceType.FILE;
            if (refType != null && i < refType.size()) {
                try { type = ReferenceType.valueOf(refType.get(i)); } catch (IllegalArgumentException ignored) {}
            }
            String note = (refNote != null && i < refNote.size()) ? refNote.get(i) : null;
            rows.add(new PaperService.RefInput(t, type, res, note));
        }
        return rows;
    }

    /**
     * AJAX one-shot save from the matrix cell popup (section + description + resources).
     * Returns the updated cell as JSON so the page can patch it in place — no full reload,
     * which is what makes Save feel instant against the remote (Supabase) database.
     */
    @PostMapping(value = "/papers/{paperId}/questions/save", headers = "X-Requested-With=XMLHttpRequest")
    @ResponseBody
    public java.util.Map<String, Object> saveQuestionCellAjax(@PathVariable Long paperId,
                                   @RequestParam String questionNumber,
                                   @RequestParam Long sectionId,
                                   @RequestParam(required = false) Integer maxMarks,
                                   @RequestParam(required = false) String description,
                                   @RequestParam(required = false) List<String> refTitle,
                                   @RequestParam(required = false) List<String> refType,
                                   @RequestParam(required = false) List<String> refResource,
                                   @RequestParam(required = false) List<String> refNote) {
        List<PaperService.RefInput> rows = buildRefInputs(refTitle, refType, refResource, refNote);
        try {
            PaperService.GridCell cell = paperService.saveQuestionCell(
                    paperId, questionNumber, sectionId, maxMarks, description, rows);
            return java.util.Map.of("ok", true, "message", "Saved " + questionNumber + ".", "cell", cell);
        } catch (ResponseStatusException e) {
            return java.util.Map.of("ok", false,
                    "message", e.getReason() == null ? "Could not save the question." : e.getReason());
        }
    }

    /** One-shot save from the matrix cell popup (non-AJAX fallback). */
    @PostMapping("/papers/{paperId}/questions/save")
    public String saveQuestionCell(@PathVariable Long paperId,
                                   @RequestParam String questionNumber,
                                   @RequestParam Long sectionId,
                                   @RequestParam(required = false) Integer maxMarks,
                                   @RequestParam(required = false) String description,
                                   @RequestParam(required = false) List<String> refTitle,
                                   @RequestParam(required = false) List<String> refType,
                                   @RequestParam(required = false) List<String> refResource,
                                   @RequestParam(required = false) List<String> refNote,
                                   RedirectAttributes ra) {
        List<PaperService.RefInput> rows = buildRefInputs(refTitle, refType, refResource, refNote);
        try {
            paperService.saveQuestionCell(paperId, questionNumber, sectionId, maxMarks, description, rows);
            ra.addFlashAttribute("flashSuccess", "Saved " + questionNumber + ".");
        } catch (ResponseStatusException e) {
            ra.addFlashAttribute("flashError", e.getReason());
        }
        return "redirect:/teacher/dashboard";
    }

    /** AJAX: fully clear a matrix cell (delete the question at that position) — patched in place. */
    @PostMapping(value = "/papers/{paperId}/questions/clear", headers = "X-Requested-With=XMLHttpRequest")
    @ResponseBody
    public java.util.Map<String, Object> clearQuestionCellAjax(@PathVariable Long paperId,
                                    @RequestParam String questionNumber) {
        paperService.clearQuestionCell(paperId, questionNumber);
        return java.util.Map.of("ok", true, "message", "Cleared " + questionNumber + ".");
    }

    /** Fully clear a matrix cell (non-AJAX fallback). */
    @PostMapping("/papers/{paperId}/questions/clear")
    public String clearQuestionCell(@PathVariable Long paperId,
                                    @RequestParam String questionNumber,
                                    RedirectAttributes ra) {
        paperService.clearQuestionCell(paperId, questionNumber);
        ra.addFlashAttribute("flashSuccess", "Cleared " + questionNumber + ".");
        return "redirect:/teacher/dashboard";
    }

    // ---------- References ----------

    @GetMapping("/questions/{questionId}/references")
    public String references(@PathVariable Long questionId, Model model) {
        Question question = paperService.requireQuestionWithPaper(questionId);
        model.addAttribute("question", question);
        model.addAttribute("paper", question.getPaperStructure());
        model.addAttribute("references", paperService.referencesOf(questionId));
        model.addAttribute("refTypes", ReferenceType.values());
        return "teacher/references";
    }

    @PostMapping("/questions/{questionId}/references")
    public String addReference(@PathVariable Long questionId,
                               @RequestParam String title,
                               @RequestParam ReferenceType type,
                               @RequestParam String resource,
                               @RequestParam(required = false) String note,
                               RedirectAttributes ra) {
        paperService.addReference(questionId, title, type, resource, note);
        ra.addFlashAttribute("flashSuccess", "Reference added.");
        return "redirect:/teacher/questions/" + questionId + "/references";
    }

    @PostMapping("/references/{id}/update")
    public String updateReference(@PathVariable Long id,
                                  @RequestParam String title,
                                  @RequestParam ReferenceType type,
                                  @RequestParam String resource,
                                  @RequestParam(required = false) String note,
                                  RedirectAttributes ra) {
        Long questionId = paperService.questionIdOfReference(id);
        paperService.updateReference(id, title, type, resource, note);
        ra.addFlashAttribute("flashSuccess", "Reference updated.");
        return "redirect:/teacher/questions/" + questionId + "/references";
    }

    @PostMapping("/references/{id}/delete")
    public String deleteReference(@PathVariable Long id, RedirectAttributes ra) {
        Long questionId = paperService.deleteReference(id);
        ra.addFlashAttribute("flashSuccess", "Reference deleted.");
        return "redirect:/teacher/questions/" + questionId + "/references";
    }
}
