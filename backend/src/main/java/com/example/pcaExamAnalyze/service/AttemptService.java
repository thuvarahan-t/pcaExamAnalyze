package com.example.pcaExamAnalyze.service;

import com.example.pcaExamAnalyze.domain.*;
import com.example.pcaExamAnalyze.repo.AttemptRepository;
import com.example.pcaExamAnalyze.repo.PaperStructureRepository;
import com.example.pcaExamAnalyze.repo.QuestionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Student marking sheets. An attempt is a cross-paper sheet: the student enters
 * the marks they scored per question (any subset — blanks allowed), across every
 * year's paper. Saving upserts the entered scores and drops any cleared ones.
 */
@Service
public class AttemptService {

    private final AttemptRepository attempts;
    private final PaperStructureRepository papers;
    private final QuestionRepository questions;

    public AttemptService(AttemptRepository attempts, PaperStructureRepository papers,
                          QuestionRepository questions) {
        this.attempts = attempts;
        this.questions = questions;
        this.papers = papers;
    }

    /** One cell of the sheet: the question at (year, Qn) plus the student's entered mark. */
    public record SheetCell(String position, Long questionId, String section, String color,
                            Integer maxMarks, Integer mark) {
        public boolean isSet() { return questionId != null; }
    }

    /** One row of the sheet: a year/paper and its four Structure cells. */
    public record SheetRow(int year, List<SheetCell> cells) {}

    /** The full sheet for one attempt, ready for the view. */
    public record SheetView(int attemptNumber, boolean saved, int obtained, int max, long percentage,
                            int answered, List<SheetRow> rows) {}

    /** Attempt numbers this student has saved, oldest first (may be empty). */
    @Transactional(readOnly = true)
    public List<Integer> savedAttemptNumbers(Long studentId) {
        return attempts.findByStudentIdOrderByAttemptNumberAsc(studentId).stream()
                .map(Attempt::getAttemptNumber).toList();
    }

    @Transactional(readOnly = true)
    public int maxAttemptNumber(Long studentId) {
        return attempts.findMaxAttemptNumber(studentId);
    }

    /** Build the marking sheet for (student, attemptNumber). Unsaved attempts render blank. */
    @Transactional(readOnly = true)
    public SheetView sheet(Long studentId, int attemptNumber) {
        Attempt attempt = attempts.findWithScoresByStudentIdAndAttemptNumber(studentId, attemptNumber).orElse(null);
        Map<Long, Integer> markByQuestion = new HashMap<>();
        int obtained = 0, max = 0, answered = 0;
        if (attempt != null) {
            for (QuestionScore s : attempt.getScores()) {
                markByQuestion.put(s.getQuestion().getId(), s.getMarksObtained());
            }
            obtained = attempt.getTotalObtained();
            max = attempt.getTotalMax();
            answered = attempt.getScores().size();
        }

        List<SheetRow> rows = new ArrayList<>();
        for (PaperStructure p : papers.findAllByOrderByYearAsc()) {
            List<Question> qs = questions.findByPaperStructureIdOrderByIdAsc(p.getId());
            Map<Integer, Question> byNum = new HashMap<>();
            for (Question q : qs) {
                Integer n = positionOf(q.getQuestionNumber());
                if (n != null) byNum.putIfAbsent(n, q);
            }
            List<SheetCell> cells = new ArrayList<>(4);
            for (int n = 1; n <= 4; n++) {
                Question q = byNum.get(n);
                if (q != null) {
                    String sec = q.getSection().getName();
                    cells.add(new SheetCell("Q" + n, q.getId(), sec, colorFor(sec),
                            q.getMaxMarks(), markByQuestion.get(q.getId())));
                } else {
                    cells.add(new SheetCell("Q" + n, null, null, null, null, null));
                }
            }
            rows.add(new SheetRow(p.getYear(), cells));
        }

        long pct = max == 0 ? 0 : Math.round((obtained * 100.0) / max);
        return new SheetView(attemptNumber, attempt != null, obtained, max, pct, answered, rows);
    }

    /**
     * Upsert the marks for (student, attemptNumber). {@code marks} holds only the
     * questions the student filled in (valid 0..maxMarks); every other question's
     * score is removed. Creates the attempt if it does not exist yet.
     */
    @Transactional
    public void saveSheet(User student, int attemptNumber, Map<Long, Integer> marks) {
        Attempt attempt = attempts.findWithScoresByStudentIdAndAttemptNumber(student.getId(), attemptNumber)
                .orElseGet(() -> new Attempt(student, attemptNumber));

        Map<Long, Question> questionById = marks.isEmpty() ? Map.of()
                : questions.findAllById(marks.keySet()).stream()
                    .collect(Collectors.toMap(Question::getId, q -> q));

        // Validate before mutating.
        for (var e : marks.entrySet()) {
            Question q = questionById.get(e.getKey());
            if (q == null) continue;
            int v = e.getValue();
            if (v < 0 || v > q.getMaxMarks()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        q.getQuestionNumber() + ": marks must be between 0 and " + q.getMaxMarks() + ".");
            }
        }

        // Index existing scores by question id.
        Map<Long, QuestionScore> existing = attempt.getScores().stream()
                .collect(Collectors.toMap(s -> s.getQuestion().getId(), s -> s, (a, b) -> a));

        // Drop scores the student cleared.
        attempt.getScores().removeIf(s -> !marks.containsKey(s.getQuestion().getId()));

        // Upsert the filled ones.
        for (var e : marks.entrySet()) {
            Question q = questionById.get(e.getKey());
            if (q == null) continue;
            QuestionScore score = existing.get(e.getKey());
            if (score != null) {
                score.setMarksObtained(e.getValue());
            } else {
                attempt.getScores().add(new QuestionScore(attempt, q, e.getValue()));
            }
        }

        attempt.recomputeTotals();
        attempts.save(attempt);
    }

    /** Create the next empty attempt for this student; returns its number. */
    @Transactional
    public int addAttempt(User student) {
        int next = attempts.findMaxAttemptNumber(student.getId()) + 1;
        attempts.save(new Attempt(student, next));
        return next;
    }

    @Transactional
    public int deleteAttempt(User student, int attemptNumber) {
        Attempt attempt = attempts.findByStudentIdAndAttemptNumber(student.getId(), attemptNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found."));
        attempts.delete(attempt);

        List<Integer> remaining = savedAttemptNumbers(student.getId());
        if (remaining.isEmpty()) return 1;
        return remaining.stream()
                .filter(n -> n < attemptNumber)
                .reduce((first, second) -> second)
                .orElse(remaining.get(0));
    }

    /** First digit (1–4) found in a question number like "Q3" / "Q3(b)". */
    private static Integer positionOf(String questionNumber) {
        if (questionNumber == null) return null;
        for (char c : questionNumber.toCharArray()) {
            if (c >= '1' && c <= '4') return c - '0';
        }
        return null;
    }

    /** Deterministic, vivid colour per section name (matches the teacher grid). */
    static String colorFor(String name) {
        int hue = Math.floorMod(name.hashCode(), 360);
        return "hsl(" + hue + ", 70%, 52%)";
    }
}
