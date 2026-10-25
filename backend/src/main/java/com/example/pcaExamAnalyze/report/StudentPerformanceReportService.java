package com.example.pcaExamAnalyze.report;

import com.example.pcaExamAnalyze.domain.*;
import com.example.pcaExamAnalyze.repo.McqSubmissionRepository;
import com.example.pcaExamAnalyze.service.McqAdminService;
import com.example.pcaExamAnalyze.service.McqExamQuestionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.springframework.core.io.ClassPathResource;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;

/** Personal cross-exam analysis. Every figure is recomputed from the current answer key. */
@Service
public class StudentPerformanceReportService {
    public static final int MAX_EXAMS = 50;
    private final McqSubmissionRepository submissions;
    private final McqExamQuestionService questions;
    private final SpringTemplateEngine templates;
    public StudentPerformanceReportService(McqSubmissionRepository submissions, McqExamQuestionService questions,
                                           SpringTemplateEngine templates) {
        this.submissions = submissions; this.questions = questions; this.templates = templates;
    }
    public record Label(String text, List<ExamReport.Img> lines) {}
    /** Accuracy below this is a weak area; at or above {@link #STRONG} is a strength. */
    public static final double WEAK = 50, STRONG = 75;
    public record Topic(Label name, int total, int correct, int wrong, int blank, String accuracy,
                        String references, boolean limited, String band, int bar) {}
    public record Answer(int number, String selected, String accepted, String state, Label topics) {}
    public record Paper(int number, Label name, String period, String attempted, int total, int correct,
                        int wrong, int blank, int free, String percentage, int bar, List<Answer> answers) {}
    public record Report(Label student, String registration, String generated, List<Paper> papers,
                         int total, int correct, int wrong, int blank, int free, String average, String combined,
                         int assessed, int tagged, int untagged, List<Topic> topics, List<Topic> priorities,
                         List<Topic> strengths, int weakCount, int midCount, int strongCount) {}
    private static final class Counter {
        final String name; int correct, wrong, blank;
        final List<String> refs = new ArrayList<>();
        Counter(String name) { this.name = name; }
        void add(String state, String ref) {
            if (state.equals("CORRECT")) correct++; else if (state.equals("INCORRECT")) wrong++; else blank++;
            refs.add(ref);
        }
        int total() { return correct + wrong + blank; }
        double accuracy() { return total() == 0 ? 0 : correct * 100.0 / total(); }
    }

    @Transactional(readOnly = true)
    public void verify(List<Long> ids, String registration, String nic) { owned(ids, registration, nic); }

    private List<McqSubmission> owned(List<Long> ids, String registration, String nic) {
        if (ids == null || ids.isEmpty() || ids.size() > MAX_EXAMS || ids.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Select between 1 and " + MAX_EXAMS + " released exams");
        if (registration == null || registration.isBlank() || nic == null || !nic.matches("[0-9]{12}"))
            throw new IllegalStateException("Open My Results again to verify your details");
        List<McqSubmission> found = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            var submission = submissions.findStudentSessionById(id)
                    .orElseThrow(() -> new IllegalStateException("A selected result is unavailable"));
            if (!submission.getRegistrationId().equalsIgnoreCase(registration.trim()) || !submission.getNic().equals(nic)
                    || submission.getStatus() != McqSubmissionStatus.SUBMITTED || !submission.getExam().isResultsPublished())
                throw new IllegalStateException("Select only your own released results");
            found.add(submission);
        }
        found.sort(Comparator.comparing(McqSubmission::getSubmittedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(McqSubmission::getId));
        return found;
    }

    @Transactional(readOnly = true)
    public Report build(List<Long> ids, String registration, String nic, BiConsumer<String, Integer> progress) {
        var selected = owned(ids, registration, nic);
        Map<String, Counter> units = new LinkedHashMap<>();
        List<Paper> papers = new ArrayList<>();
        int total = 0, correct = 0, wrong = 0, blank = 0, free = 0, tagged = 0, assessed = 0;
        double sumPercentages = 0;
        for (var submission : selected) {
            var exam = submission.getExam();
            var tags = questions.tagsByExam(exam.getId());
            var meta = questions.meta(exam.getId());
            int paperCorrect = 0, paperWrong = 0, paperBlank = 0, paperFree = 0;
            List<Answer> answers = new ArrayList<>();
            int paperNumber = papers.size() + 1;
            for (int q = 1; q <= exam.getTotalQuestions(); q++) {
                Integer choice = submission.getAnswers().get(q);
                if (choice != null && (choice < 1 || choice > exam.getOptionsPerQuestion())) choice = null;
                var accepted = new TreeSet<>(exam.correctOptions(q));
                boolean isFree = exam.isFreeMark(q);
                String state = isFree ? "FREE MARK" : choice == null ? "UNANSWERED" : accepted.contains(choice) ? "CORRECT" : "INCORRECT";
                if (isFree) { paperFree++; paperCorrect++; }
                else if (state.equals("CORRECT")) paperCorrect++;
                else if (state.equals("INCORRECT")) paperWrong++;
                else paperBlank++;
                Set<String> unitNames = new LinkedHashSet<>();
                for (var tag : tags.getOrDefault(q, List.of())) {
                    String unit = clean(tag.unitName());
                    if (unit.isEmpty()) continue;
                    unitNames.add(unit);
                }
                // Older exams retain metadata without structured syllabus tags. Do not guess associations.
                if (unitNames.isEmpty() && meta.containsKey(q)) {
                    for (String unit : meta.get(q).unitList()) if (!clean(unit).isEmpty()) unitNames.add(clean(unit));
                }
                if (!isFree) {
                    assessed++;
                    if (!unitNames.isEmpty()) tagged++;
                    String ref = "E" + paperNumber + " Q" + q;
                    add(units, unitNames, state, ref);
                }
                answers.add(new Answer(q, choice == null ? "-" : choice.toString(),
                        isFree ? "Free mark" : accepted.isEmpty() ? "Key not set" : accepted.toString().replaceAll("[\\[\\]]", ""),
                        state, label(unitNames.isEmpty() ? "Not tagged" : String.join("; ", unitNames), 42, 9, false)));
            }
            int count = exam.getTotalQuestions();
            double percentage = count == 0 ? 0 : paperCorrect * 100.0 / count;
            sumPercentages += percentage;
            papers.add(new Paper(paperNumber, label(exam.getName(), 54, 12, true),
                    exam.getExamMonth() + " " + exam.getExamYear(), McqAdminService.formatPublic(submission.getSubmittedAt()),
                    count, paperCorrect, paperWrong, paperBlank, paperFree, pct(percentage), (int)Math.round(percentage), answers));
            total += count; correct += paperCorrect; wrong += paperWrong; blank += paperBlank; free += paperFree;
            progress.accept("Analysing exam " + paperNumber + " of " + selected.size(), 5 + paperNumber * 65 / selected.size());
        }
        List<Topic> topicRows = rows(units);
        List<Topic> priorities = topicRows.stream().filter(t -> t.wrong() + t.blank() > 0).limit(3).toList();
        List<Topic> strengths = topicRows.stream().filter(t -> t.band().equals("Strong"))
                .sorted(Comparator.comparingInt(Topic::bar).reversed().thenComparing(Comparator.comparingInt(Topic::total).reversed()))
                .limit(3).toList();
        return new Report(label(selected.getFirst().getStudentName(), 52, 16, true), registration,
                McqAdminService.formatPublic(Instant.now()), papers, total, correct, wrong, blank, free,
                pct(sumPercentages / papers.size()), pct(total == 0 ? 0 : correct * 100.0 / total),
                assessed, tagged, assessed - tagged, topicRows, priorities,
                strengths, count(topicRows, "Weak"), count(topicRows, "Needs practice"), count(topicRows, "Strong"));
    }

    private static void add(Map<String, Counter> counters, Set<String> names, String state, String ref) {
        for (String name : names) counters.computeIfAbsent(name, Counter::new).add(state, ref);
    }
    private static List<Topic> rows(Map<String, Counter> map) {
        return map.values().stream().sorted(Comparator.comparingDouble(Counter::accuracy)
                        .thenComparing(Comparator.comparingInt(Counter::total).reversed()).thenComparing(c -> c.name))
                .map(c -> new Topic(label(c.name, 44, 9, false), c.total(), c.correct, c.wrong, c.blank,
                        pct(c.accuracy()), String.join(", ", c.refs.subList(0, Math.min(12, c.refs.size())))
                            + (c.refs.size() > 12 ? " ... (full mapping in question appendix)" : ""), c.total() < 3,
                        band(c.accuracy()), (int) Math.round(c.accuracy()))).toList();
    }
    private static String band(double accuracy) {
        return accuracy < WEAK ? "Weak" : accuracy < STRONG ? "Needs practice" : "Strong";
    }
    private static int count(List<Topic> rows, String band) {
        return (int) rows.stream().filter(t -> t.band().equals(band)).count();
    }
    private static String clean(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", " "); }
    private static String pct(double value) { return String.format(Locale.ENGLISH, "%.1f%%", value); }
    static Label label(String text, int width, double size, boolean bold) {
        text = text == null || text.isBlank() ? "-" : text;
        if (!TextImages.hasTamil(text)) return new Label(text, List.of());
        List<ExamReport.Img> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (!line.isEmpty() && line.length() + word.length() > width) {
                lines.add(TextImages.render(line.toString(), bold, size, new Color(23,43,67))); line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(TextImages.render(line.toString(), bold, size, new Color(23,43,67)));
        lines.removeIf(Objects::isNull);
        return new Label(text, lines);
    }

    @Transactional(readOnly = true)
    public ExamReportPdfService.Pdf render(List<Long> ids, String registration, String nic, BiConsumer<String,Integer> progress) {
        var report = build(ids, registration, nic, progress);
        progress.accept("Formatting subject analysis", 78);
        var ctx = new Context(Locale.ENGLISH); ctx.setVariable("r", report);
        String html = templates.process("report/student-performance", ctx);
        try (var out = new ByteArrayOutputStream()) {
            var builder = new PdfRendererBuilder(); builder.useFastMode();
            for (int weight : new int[]{400,700}) {
                String path = "report/fonts/Inter-" + weight + ".ttf";
                builder.useFont(() -> { try { return new ClassPathResource(path).getInputStream(); }
                    catch (java.io.IOException ex) { throw new IllegalStateException(ex); } }, "Inter", weight,
                        com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.NORMAL, true);
            }
            var parsed = Jsoup.parse(html); parsed.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml);
            builder.withW3cDocument(new W3CDom().fromJsoup(parsed), "/"); builder.toStream(out);
            progress.accept("Writing your performance PDF", 90); builder.run(); progress.accept("Finishing your report", 98);
            return new ExamReportPdfService.Pdf(out.toByteArray(), "PCA-Performance-Report.pdf");
        } catch (java.io.IOException ex) { throw new IllegalStateException("Could not create performance report", ex); }
    }
}
