package com.example.pcaExamAnalyze.service;

import com.example.pcaExamAnalyze.domain.*;
import com.example.pcaExamAnalyze.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Teacher-side CRUD over papers, questions and references.
 */
@Service
public class PaperService {

    private final PaperStructureRepository papers;
    private final QuestionRepository questions;
    private final QuestionReferenceRepository references;
    private final SectionRepository sections;

    public PaperService(PaperStructureRepository papers,
                        QuestionRepository questions,
                        QuestionReferenceRepository references,
                        SectionRepository sections) {
        this.papers = papers;
        this.questions = questions;
        this.references = references;
        this.sections = sections;
    }

    // ---- Teacher grid (year × Structured 1–4 matrix) ----

    /** A reference as the cell-editor popup needs it (type is the enum name). */
    public record RefView(String title, String type, String resource, String note) {}

    /** One cell of the matrix: the question (if any) assigned to (year, Qn). */
    public record GridCell(String position, Long questionId, Long sectionId, String section, String color,
                           String description, Integer maxMarks, List<RefView> refs, String refsJson) {
        public boolean isSet() { return section != null; }
        public long refCount() { return refs == null ? 0 : refs.size(); }
    }

    /** A reference row submitted from the popup. */
    public record RefInput(String title, ReferenceType type, String resource, String note) {}

    /** One row of the matrix: a year/paper with its four Structured cells. */
    public record GridRow(int year, Long paperId, List<GridCell> cells) {}

    @Transactional(readOnly = true)
    public List<GridRow> teacherGrid() {
        List<GridRow> rows = new ArrayList<>();
        for (PaperStructure p : papers.findAllByOrderByYearAsc()) {
            List<Question> qs = questions.findByPaperStructureIdOrderByIdAsc(p.getId());
            java.util.Map<Integer, Question> byNum = new java.util.HashMap<>();
            for (Question q : qs) {
                Integer n = positionOf(q.getQuestionNumber());
                if (n != null) byNum.putIfAbsent(n, q);
            }
            List<GridCell> cells = new ArrayList<>(4);
            for (int n = 1; n <= 4; n++) {
                Question q = byNum.get(n);
                if (q != null) {
                    String sec = q.getSection().getName();
                    List<RefView> refs = references.findByQuestionIdOrderByIdAsc(q.getId()).stream()
                            .map(r -> new RefView(r.getTitle(), r.getType().name(), r.getResource(), r.getNote()))
                            .toList();
                    cells.add(new GridCell("Q" + n, q.getId(), q.getSection().getId(), sec, colorFor(sec),
                            q.getDescription(), q.getMaxMarks(), refs, toJson(refs)));
                } else {
                    cells.add(new GridCell("Q" + n, null, null, null, null, null, null, List.of(), "[]"));
                }
            }
            rows.add(new GridRow(p.getYear(), p.getId(), cells));
        }
        return rows;
    }

    /** Minimal JSON for the cell popup's data-refs attribute (no Jackson dependency at compile time). */
    private static String toJson(List<RefView> refs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < refs.size(); i++) {
            RefView r = refs.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"title\":").append(jsonStr(r.title()))
              .append(",\"type\":").append(jsonStr(r.type()))
              .append(",\"resource\":").append(jsonStr(r.resource()))
              .append(",\"note\":").append(jsonStr(r.note()))
              .append('}');
        }
        return sb.append(']').toString();
    }

    private static String jsonStr(String s) {
        if (s == null) return "\"\"";
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }

    /**
     * Create-or-update the Structured-{n} question of a paper in one shot, and
     * replace its references with the submitted set. Powers the cell popup's Save.
     */
    @Transactional
    public Long saveQuestionCell(Long paperId, String questionNumber, Long sectionId,
                                 Integer maxMarks, String description, List<RefInput> refs) {
        PaperStructure paper = requirePaper(paperId);
        Section section = requireSection(sectionId);
        Integer pos = positionOf(questionNumber);

        Question q = questions.findByPaperStructureIdOrderByIdAsc(paperId).stream()
                .filter(x -> Objects.equals(positionOf(x.getQuestionNumber()), pos))
                .findFirst().orElse(null);
        if (q == null) {
            q = new Question(paper, questionNumber.trim(), section, normalizeMarks(maxMarks), blankToNull(description));
        } else {
            q.setQuestionNumber(questionNumber.trim());
            q.setSection(section);
            q.setMaxMarks(normalizeMarks(maxMarks));
            q.setDescription(blankToNull(description));
        }
        q = questions.save(q);

        // Replace references with the submitted rows (skip blank rows).
        references.deleteAll(references.findByQuestionIdOrderByIdAsc(q.getId()));
        for (RefInput r : refs) {
            if (r.title() == null || r.title().isBlank() || r.resource() == null || r.resource().isBlank()) {
                continue;
            }
            references.save(new QuestionReference(q, r.title().trim(), r.type(),
                    r.resource().trim(), blankToNull(r.note())));
        }
        return q.getId();
    }

    /** First digit (1–4) found in a question number like "Q3" / "Q3(b)". */
    private static Integer positionOf(String questionNumber) {
        if (questionNumber == null) return null;
        for (char c : questionNumber.toCharArray()) {
            if (c >= '1' && c <= '4') return c - '0';
        }
        return null;
    }

    /** Deterministic, vivid colour per section name (stable across pages). */
    private static String colorFor(String name) {
        int hue = Math.floorMod(name.hashCode(), 360);
        return "hsl(" + hue + ", 70%, 52%)";
    }

    // ---- Sections (shared across all papers) ----

    @Transactional(readOnly = true)
    public List<Section> allSections() {
        return sections.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Section requireSection(Long id) {
        return sections.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not found"));
    }

    /** Add a section by name, or return the existing one if the name already exists (case-insensitive). */
    @Transactional
    public Section addSection(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Section name is required.");
        }
        return sections.findByNameIgnoreCase(clean).orElseGet(() -> sections.save(new Section(clean)));
    }

    @Transactional
    public void deleteSection(Long id) {
        Section s = requireSection(id);
        if (questions.existsBySectionId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "“" + s.getName() + "” is used by one or more questions — reassign them first.");
        }
        sections.delete(s);
    }

    // ---- Papers ----

    @Transactional(readOnly = true)
    public List<PaperStructure> allPapers() {
        return papers.findAllByOrderByYearAsc();
    }

    /** A paper plus its aggregate counts, computed inside the transaction (no lazy access in views). */
    public record PaperSummary(PaperStructure paper, long questionCount, long referenceCount, int maxMarks) {
        public Long id() { return paper.getId(); }
        public Integer year() { return paper.getYear(); }
        public String subject() { return paper.getSubject(); }
        public String title() { return paper.getTitle(); }
    }

    @Transactional(readOnly = true)
    public List<PaperSummary> paperSummaries() {
        return papers.findAllByOrderByYearAsc().stream().map(p -> {
            List<Question> qs = questions.findByPaperStructureIdOrderByIdAsc(p.getId());
            int max = qs.stream().mapToInt(Question::getMaxMarks).sum();
            long refs = references.countByQuestion_PaperStructureId(p.getId());
            return new PaperSummary(p, qs.size(), refs, max);
        }).toList();
    }

    @Transactional(readOnly = true)
    public long totalQuestions() {
        return questions.count();
    }

    @Transactional(readOnly = true)
    public long totalReferences() {
        return references.count();
    }

    @Transactional(readOnly = true)
    public long questionCount(Long paperId) {
        return questions.countByPaperStructureId(paperId);
    }

    @Transactional(readOnly = true)
    public long referenceCount(Long paperId) {
        return references.countByQuestion_PaperStructureId(paperId);
    }

    @Transactional(readOnly = true)
    public PaperStructure requirePaper(Long id) {
        return papers.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paper not found"));
    }

    @Transactional(readOnly = true)
    public PaperStructure requirePaperWithQuestions(Long id) {
        return papers.findWithQuestionsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paper not found"));
    }

    @Transactional
    public PaperStructure createPaper(Integer year, String subject, String title) {
        if (papers.existsByYear(year)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A paper for " + year + " already exists.");
        }
        String subj = (subject == null || subject.isBlank()) ? "Physics" : subject.trim();
        return papers.save(new PaperStructure(year, subj, title.trim()));
    }

    @Transactional
    public void updatePaper(Long id, Integer year, String subject, String title) {
        PaperStructure p = requirePaper(id);
        if (!p.getYear().equals(year) && papers.existsByYear(year)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A paper for " + year + " already exists.");
        }
        p.setYear(year);
        p.setSubject((subject == null || subject.isBlank()) ? "Physics" : subject.trim());
        p.setTitle(title.trim());
        papers.save(p);
    }

    @Transactional
    public void deletePaper(Long id) {
        papers.delete(requirePaper(id));
    }

    // ---- Questions ----

    @Transactional(readOnly = true)
    public List<Question> questionsOf(Long paperId) {
        return questions.findByPaperStructureIdOrderByIdAsc(paperId);
    }

    @Transactional(readOnly = true)
    public Question requireQuestion(Long id) {
        return questions.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
    }

    /** Question with its paper eagerly loaded (safe to read in views). */
    @Transactional(readOnly = true)
    public Question requireQuestionWithPaper(Long id) {
        return questions.findWithPaperById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
    }

    @Transactional(readOnly = true)
    public Long paperIdOfQuestion(Long questionId) {
        return requireQuestion(questionId).getPaperStructure().getId();
    }

    @Transactional(readOnly = true)
    public Long questionIdOfReference(Long referenceId) {
        return requireReference(referenceId).getQuestion().getId();
    }

    @Transactional
    public Question addQuestion(Long paperId, String questionNumber, Long sectionId,
                                Integer maxMarks, String description) {
        PaperStructure paper = requirePaper(paperId);
        Section section = requireSection(sectionId);
        Question q = new Question(paper, questionNumber.trim(), section,
                normalizeMarks(maxMarks), blankToNull(description));
        return questions.save(q);
    }

    @Transactional
    public void updateQuestion(Long id, String questionNumber, Long sectionId,
                               Integer maxMarks, String description) {
        Question q = requireQuestion(id);
        q.setQuestionNumber(questionNumber.trim());
        q.setSection(requireSection(sectionId));
        q.setMaxMarks(normalizeMarks(maxMarks));
        q.setDescription(blankToNull(description));
        questions.save(q);
    }

    private static int normalizeMarks(Integer maxMarks) {
        return (maxMarks == null || maxMarks < 1) ? Question.DEFAULT_MAX_MARKS : maxMarks;
    }

    @Transactional
    public Long deleteQuestion(Long id) {
        Question q = requireQuestion(id);
        Long paperId = q.getPaperStructure().getId();
        questions.delete(q);
        return paperId;
    }

    // ---- References ----

    @Transactional(readOnly = true)
    public List<QuestionReference> referencesOf(Long questionId) {
        return references.findByQuestionIdOrderByIdAsc(questionId);
    }

    @Transactional(readOnly = true)
    public QuestionReference requireReference(Long id) {
        return references.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reference not found"));
    }

    @Transactional
    public void addReference(Long questionId, String title, ReferenceType type, String resource, String note) {
        Question q = requireQuestion(questionId);
        references.save(new QuestionReference(q, title.trim(), type, resource.trim(), blankToNull(note)));
    }

    @Transactional
    public void updateReference(Long id, String title, ReferenceType type, String resource, String note) {
        QuestionReference r = requireReference(id);
        r.setTitle(title.trim());
        r.setType(type);
        r.setResource(resource.trim());
        r.setNote(blankToNull(note));
        references.save(r);
    }

    @Transactional
    public Long deleteReference(Long id) {
        QuestionReference r = requireReference(id);
        Long questionId = r.getQuestion().getId();
        references.delete(r);
        return questionId;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
