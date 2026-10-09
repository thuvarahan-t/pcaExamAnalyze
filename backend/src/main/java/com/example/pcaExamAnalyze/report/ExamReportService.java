package com.example.pcaExamAnalyze.report;

import com.example.pcaExamAnalyze.domain.McqExam;
import com.example.pcaExamAnalyze.domain.McqExamQuestion;
import com.example.pcaExamAnalyze.domain.McqSubImage;
import com.example.pcaExamAnalyze.domain.McqSubmission;
import com.example.pcaExamAnalyze.domain.McqSubmissionStatus;
import com.example.pcaExamAnalyze.repo.McqExamRepository;
import com.example.pcaExamAnalyze.repo.McqQuestionMeta;
import com.example.pcaExamAnalyze.repo.McqSubmissionRepository;
import com.example.pcaExamAnalyze.service.McqAdminService;
import com.example.pcaExamAnalyze.service.McqExamQuestionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Turns an exam's submissions into the figures of the PDF analysis report. Marks are recomputed from
 * the live answer key (one mark per question, free-mark questions count for everybody), so the report
 * is right even if some stored scores are stale.
 */
@Service
public class ExamReportService {

    static final ZoneId ZONE = McqAdminService.PCA_ZONE;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm a", Locale.ENGLISH).withZone(ZONE);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH).withZone(ZONE);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH).withZone(ZONE);

    /** Sri Lanka's 25 districts (same list the student form uses). */
    static final List<String> DISTRICTS = List.of(
            "Colombo", "Gampaha", "Kalutara", "Kandy", "Matale", "Nuwara Eliya", "Galle", "Matara", "Hambantota",
            "Jaffna", "Kilinochchi", "Mannar", "Vavuniya", "Mullaitivu", "Batticaloa", "Ampara", "Trincomalee",
            "Kurunegala", "Puttalam", "Anuradhapura", "Polonnaruwa", "Badulla", "Monaragala", "Ratnapura", "Kegalle");

    static final double PASS_PCT = 50.0;

    private final McqExamRepository exams;
    private final McqSubmissionRepository submissions;
    private final McqExamQuestionService questionService;

    public ExamReportService(McqExamRepository exams, McqSubmissionRepository submissions,
                             McqExamQuestionService questionService) {
        this.exams = exams;
        this.submissions = submissions;
        this.questionService = questionService;
    }

    /** A submitted paper with its recomputed marks. */
    private record Scored(McqSubmission s, int correct, int wrong, int blank, double pct, Double seconds) {
        String grade() { return gradeOf(pct); }
    }

    @Transactional(readOnly = true)
    public ExamReport build(Long examId, String logo, String banner, BiConsumer<String, Integer> progress) {
        progress.accept("Analysing the results", 8);
        McqExam exam = exams.findById(examId).orElseThrow(() -> new IllegalArgumentException("Exam not found"));
        int total = exam.getTotalQuestions();
        int options = exam.getOptionsPerQuestion();
        List<McqSubmission> all = submissions.findByExamIdOrderByCreatedAtDesc(examId);
        List<McqSubmission> done = all.stream().filter(s -> s.getStatus() == McqSubmissionStatus.SUBMITTED).toList();
        int inProgress = all.size() - done.size();

        // ---------------------------------------------------------------- marks per student
        List<Scored> scored = new ArrayList<>();
        for (McqSubmission s : done) {
            int correct = 0, wrong = 0, blank = 0;
            for (int q = 1; q <= total; q++) {
                Integer sel = validSelection(s.getAnswers().get(q), options);
                if (exam.isFreeMark(q) || sel != null && exam.correctOptions(q).contains(sel)) correct++;
                else if (sel == null) blank++;
                else wrong++;
            }
            Double seconds = s.getStartedAt() != null && s.getSubmittedAt() != null
                    ? (double) Duration.between(s.getStartedAt(), s.getSubmittedAt()).toSeconds() : null;
            if (seconds != null && seconds <= 0) seconds = null;
            scored.add(new Scored(s, correct, wrong, blank, total == 0 ? 0 : correct * 100.0 / total, seconds));
        }
        scored.sort(Comparator.comparingInt(Scored::correct).reversed()
                .thenComparing(x -> x.seconds() == null ? Double.MAX_VALUE : x.seconds())
                .thenComparing(x -> x.s().getStudentName().toLowerCase(Locale.ROOT)));
        int n = scored.size();

        // ---------------------------------------------------------------- summary
        double[] pcts = scored.stream().mapToDouble(Scored::pct).toArray();
        double meanPct = n == 0 ? 0 : IntStream.range(0, n).mapToDouble(i -> pcts[i]).average().orElse(0);
        double meanMarks = meanPct * total / 100.0;
        double median = median(pcts);
        double std = std(pcts, meanPct);
        int passCount = (int) scored.stream().filter(x -> x.pct() >= PASS_PCT).count();
        Scored best = n == 0 ? null : scored.getFirst();
        Scored worst = n == 0 ? null : scored.getLast();
        List<Double> secs = scored.stream().map(Scored::seconds).filter(Objects::nonNull).toList();
        String avgTime = secs.isEmpty() ? "-" : duration(secs.stream().mapToDouble(d -> d).average().orElse(0));

        ExamReport.Summary summary = new ExamReport.Summary(n, inProgress, f1(meanMarks) + " / " + total, p1(meanPct), p1(median),
                f1(std), best == null ? "-" : best.correct() + " / " + total, best == null ? "-" : p1(best.pct()),
                best == null ? "-" : best.s().getStudentName(), worst == null ? "-" : worst.correct() + " / " + total,
                worst == null ? "-" : p1(worst.pct()), n == 0 ? "-" : p1(passCount * 100.0 / n), passCount, avgTime);

        // ---------------------------------------------------------------- histogram + grades
        int[] decile = new int[10];
        for (double p : pcts) decile[Math.min(9, (int) (p / 10))]++;
        List<ExamReport.Band> histogram = bands(IntStream.range(0, 10).mapToObj(i -> (i * 10) + "-" + ((i + 1) * 10)).toList(),
                decile, n);
        String[] gradeNames = {"A", "B", "C", "S", "F"};
        String[] gradeRanges = {"75% and above", "65% - 74%", "50% - 64%", "35% - 49%", "Below 35%"};
        String[] gradeColors = {"#10b981", "#22b8e6", "#3b5bff", "#f59e0b", "#ef4444"};
        int[] gradeCount = new int[5];
        for (Scored x : scored) gradeCount[indexOfGrade(x.pct())]++;
        int gradeMax = Math.max(1, IntStream.of(gradeCount).max().orElse(1));
        List<ExamReport.GradeRow> grades = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            grades.add(new ExamReport.GradeRow(gradeNames[i], gradeRanges[i], gradeCount[i],
                    n == 0 ? "0%" : p1(gradeCount[i] * 100.0 / n), gradeCount[i] * 100 / gradeMax, gradeColors[i]));
        }

        // ---------------------------------------------------------------- participation groups
        List<ExamReport.Group> byBatch = groups(scored, x -> blankTo(x.s().getBatch(), "Unknown"), 12);
        List<ExamReport.Group> byStream = groups(scored, x -> blankTo(x.s().getStream(), "Unknown"), 6);

        double allotted = exam.getDurationMinutes() == null ? 0 : exam.getDurationMinutes();
        ExamReport.TimeStats timeStats = secs.isEmpty() ? new ExamReport.TimeStats("-", "-", "-", allotted > 0 ? exam.getDurationMinutes() + " minutes" : "No fixed limit", false)
                : new ExamReport.TimeStats(avgTime, duration(Collections.min(secs)), duration(Collections.max(secs)),
                allotted > 0 ? exam.getDurationMinutes() + " minutes" : "No fixed limit", true);
        List<ExamReport.Band> timeBuckets = timeBuckets(secs, allotted);
        List<ExamReport.Band> timeline = timeline(done);

        // ---------------------------------------------------------------- districts
        Map<String, List<Scored>> byDistrict = new LinkedHashMap<>();
        for (Scored x : scored) byDistrict.computeIfAbsent(canonicalDistrict(x.s().getDistrict()), k -> new ArrayList<>()).add(x);
        List<ExamReport.DistrictRow> districtRows = new ArrayList<>();
        byDistrict.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue()))
                .sorted(Comparator.<Map.Entry<String, List<Scored>>>comparingDouble(
                                e -> -e.getValue().stream().mapToDouble(Scored::pct).average().orElse(0))
                        .thenComparing(e -> -e.getValue().size()))
                .forEach(e -> {
                    List<Scored> list = e.getValue();
                    double avg = list.stream().mapToDouble(Scored::pct).average().orElse(0);
                    int hi = list.stream().mapToInt(Scored::correct).max().orElse(0);
                    int lo = list.stream().mapToInt(Scored::correct).min().orElse(0);
                    long pass = list.stream().filter(x -> x.pct() >= PASS_PCT).count();
                    districtRows.add(new ExamReport.DistrictRow(districtRows.size() + 1, e.getKey(), list.size(),
                            p1(list.size() * 100.0 / n), f1(avg * total / 100.0), p1(avg), hi + " / " + total, lo + " / " + total,
                            p1(pass * 100.0 / list.size()), (int) Math.round(avg)));
                });
        Set<String> present = byDistrict.keySet().stream().map(d -> d.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<String> missingDistricts = DISTRICTS.stream().filter(d -> !present.contains(d.toLowerCase(Locale.ROOT))).toList();

        // ---------------------------------------------------------------- questions
        Map<Integer, McqQuestionMeta> meta = exam.usesQuestionImages() ? questionService.meta(examId) : Map.of();
        Map<Integer, List<McqExamQuestionService.TagView>> tagMap = exam.usesQuestionImages() ? questionService.tagsByExam(examId) : Map.of();
        Map<Integer, List<Long>> subIds = exam.usesQuestionImages() ? questionService.subImageIds(examId) : Map.of();

        int groupSize = Math.max(1, (int) Math.ceil(n * 0.27));
        boolean canDiscriminate = n >= 10;
        List<ExamReport.QuestionRow> questions = new ArrayList<>();
        Map<Integer, byte[]> rawImages = new TreeMap<>();
        Map<Integer, String[]> rawKeys = new TreeMap<>();
        Map<Integer, List<byte[]>> rawSubs = new TreeMap<>();
        Map<Integer, List<String[]>> rawSubKeys = new TreeMap<>();
        if (exam.usesQuestionImages()) {
            for (int q = 1; q <= total; q++) {
                Optional<McqExamQuestion> img = questionService.findImage(examId, q);
                if (img.isPresent()) {
                    McqExamQuestion row = img.get();
                    Optional<byte[]> direct = row.getData() != null && row.getData().length > 0 ? Optional.of(row.getData()) : Optional.empty();
                    if (direct.isPresent()) rawImages.put(q, direct.get());
                    else rawKeys.put(q, new String[]{row.getStorageKey(), row.getContentType()});
                }
                for (Long id : subIds.getOrDefault(q, List.of())) {
                    Optional<McqSubImage> sub = questionService.subImage(examId, q, id);
                    if (sub.isEmpty()) continue;
                    McqSubImage si = sub.get();
                    if (si.getData() != null && si.getData().length > 0) rawSubs.computeIfAbsent(q, k -> new ArrayList<>()).add(si.getData());
                    else rawSubKeys.computeIfAbsent(q, k -> new ArrayList<>()).add(new String[]{si.getStorageKey(), si.getContentType()});
                }
            }
        }
        progress.accept("Preparing the question images", 20);
        // Heavy work (R2 download, decode, down-scale, base64) needs no database session: do it in parallel.
        Map<Integer, String> mainUris = new ConcurrentHashMap<>();
        Map<Integer, List<String>> subUris = new ConcurrentHashMap<>();
        AtomicInteger preparedImages = new AtomicInteger();
        IntStream.rangeClosed(1, total).parallel().forEach(q -> {
            byte[] bytes = rawImages.get(q);
            if (bytes == null && rawKeys.containsKey(q)) bytes = questionService.imageBytesByKey(rawKeys.get(q)[0]).orElse(null);
            String uri = bytes == null ? null : ReportImages.toDataUri(bytes, 1500);
            if (uri != null) mainUris.put(q, uri);
            List<String> subs = new ArrayList<>();
            for (byte[] b : rawSubs.getOrDefault(q, List.of())) {
                String u = ReportImages.toDataUri(b, 800);
                if (u != null) subs.add(u);
            }
            for (String[] key : rawSubKeys.getOrDefault(q, List.of())) {
                byte[] b = questionService.imageBytesByKey(key[0]).orElse(null);
                String u = b == null ? null : ReportImages.toDataUri(b, 800);
                if (u != null) subs.add(u);
            }
            if (!subs.isEmpty()) subUris.put(q, subs);
            int finished = preparedImages.incrementAndGet();
            progress.accept("Preparing the question images", 20 + finished * 35 / Math.max(1, total));
        });

        progress.accept("Calculating question statistics", 58);
        for (int q = 1; q <= total; q++) {
            Set<Integer> correctSet = exam.correctOptions(q);
            boolean free = exam.isFreeMark(q);
            int[] counts = new int[options + 1];
            int unanswered = 0, correct = 0, correctTop = 0, correctBottom = 0, correctMid = 0;
            double timeSum = 0;
            int timed = 0;
            for (int i = 0; i < n; i++) {
                McqSubmission s = scored.get(i).s();
                Integer sel = validSelection(s.getAnswers().get(q), options);
                if (sel == null) unanswered++; else counts[sel]++;
                boolean right = free || sel != null && correctSet.contains(sel);
                if (right) {
                    correct++;
                    if (i < groupSize) correctTop++;
                    else if (i >= n - groupSize) correctBottom++;
                    else correctMid++;
                }
                Integer t = exam.usesQuestionImages() ? s.getQuestionTimes().get(q) : null;
                if (t != null && t > 0) { timeSum += t; timed++; }
            }
            int midSize = Math.max(0, n - 2 * groupSize);
            double correctPct = n == 0 ? 0 : correct * 100.0 / n;
            int maxCount = Math.max(1, Math.max(unanswered, IntStream.of(counts).max().orElse(1)));
            int topWrongOpt = -1, topWrongCount = 0;
            for (int o = 1; o <= options; o++) {
                if (!correctSet.contains(o) && counts[o] > topWrongCount) { topWrongCount = counts[o]; topWrongOpt = o; }
            }
            List<ExamReport.OptionRow> optionRows = new ArrayList<>();
            for (int o = 1; o <= options; o++) {
                optionRows.add(new ExamReport.OptionRow(o, counts[o], n == 0 ? "0%" : p1(counts[o] * 100.0 / n),
                        counts[o] * 100 / maxCount, correctSet.contains(o), o == topWrongOpt));
            }
            ExamReport.OptionRow blankRow = new ExamReport.OptionRow(0, unanswered, n == 0 ? "0%" : p1(unanswered * 100.0 / n),
                    unanswered * 100 / maxCount, false, false);

            Double disc = null;
            if (canDiscriminate && !free) {
                double pt = correctTop / (double) groupSize;
                double pb = correctBottom / (double) groupSize;
                disc = pt - pb;
            }
            String discLabel = disc == null ? "n/a" : disc >= 0.40 ? "Excellent" : disc >= 0.30 ? "Good" : disc >= 0.20 ? "Fair" : disc >= 0 ? "Poor" : "Negative";
            String discClass = disc == null ? "na" : disc >= 0.30 ? "good" : disc >= 0.20 ? "fair" : "poor";

            McqQuestionMeta m = meta.get(q);
            Integer expected = m == null ? null : m.timeSeconds();
            Integer avgSec = timed == 0 ? null : (int) Math.round(timeSum / timed);
            String verdict = avgSec == null || expected == null || expected <= 0 ? ""
                    : avgSec > expected * 1.25 ? "Slower than expected" : avgSec < expected * 0.75 ? "Faster than expected" : "As expected";

            String difficulty = free ? "Free mark" : correctPct >= 70 ? "Easy" : correctPct >= 40 ? "Moderate" : "Difficult";
            String diffClass = free ? "free" : correctPct >= 70 ? "easy" : correctPct >= 40 ? "mod" : "hard";

            progress.accept("Calculating question statistics", 58 + q * 18 / Math.max(1, total));

            String flag = "";
            if (!free && n >= 10) {
                if (disc != null && disc < 0) flag = "Review: stronger students scored lower than weaker students on this question - check the answer key and wording.";
                else if (correctPct < 25 && topWrongOpt > 0 && counts[topWrongOpt] > correct) flag = "Review: option " + topWrongOpt + " was chosen more than the keyed answer - confirm the key.";
                else if (disc != null && disc < 0.2) flag = "Low discrimination: this question does not separate strong and weak students well.";
            }

            List<ExamReport.TagRow> tags = new ArrayList<>();
            for (McqExamQuestionService.TagView t : tagMap.getOrDefault(q, List.of())) {
                tags.add(new ExamReport.TagRow(nz(t.unitName()), nz(t.levelName()), nz(t.competency()), t.contents(), t.outcomes()));
            }
            if (tags.isEmpty() && m != null && !m.unitList().isEmpty()) {
                tags.add(new ExamReport.TagRow(String.join(", ", m.unitList()), String.join(", ", m.competencyLevelList()), "",
                        m.contentList(), m.outcomeList()));
            }

            String insight = insight(q, free, n, correctPct, difficulty, topWrongOpt, topWrongCount, disc,
                    n == 0 ? 0 : correctTop * 100.0 / groupSize, n == 0 || groupSize == 0 ? 0 : correctBottom * 100.0 / groupSize,
                    avgSec, expected, verdict);

            Double weight = m == null ? null : m.weight();
            questions.add(new ExamReport.QuestionRow(q, "q" + q, correct, n - unanswered, unanswered, n, p1(correctPct),
                    (int) Math.round(correctPct), n == 0 ? "-" : p1((n - unanswered) * 100.0 / n), difficulty, diffClass,
                    correctSet.isEmpty() ? (free ? "Free mark" : "Not set") : correctSet.stream().map(String::valueOf).collect(Collectors.joining(" / ")),
                    optionRows, blankRow, topWrongOpt > 0 ? "Option " + topWrongOpt + " (" + p1(topWrongCount * 100.0 / Math.max(1, n)) + ")" : "-",
                    disc == null ? "n/a" : String.format(Locale.ROOT, "%.2f", disc), discLabel, discClass,
                    canDiscriminate && !free ? p1(correctTop * 100.0 / groupSize) : "n/a",
                    canDiscriminate && !free && midSize > 0 ? p1(correctMid * 100.0 / midSize) : "n/a",
                    canDiscriminate && !free ? p1(correctBottom * 100.0 / groupSize) : "n/a",
                    weight == null ? "-" : McqExamQuestion.weightText(weight) + " / 5", weight == null ? 0 : (int) Math.round(weight / 5 * 100),
                    expected == null ? "-" : clock(expected), avgSec == null ? "-" : clock(avgSec),
                    verdict, free, tags, insight, mainUris.get(q), subUris.getOrDefault(q, List.of()), flag));
        }

        // ---------------------------------------------------------------- syllabus units
        List<ExamReport.UnitRow> units = unitRows(questions);

        List<ExamReport.QuestionRow> ranked = questions.stream().filter(r -> !r.freeMark() && r.participants() > 0)
                .sorted(Comparator.comparingDouble(r -> Double.parseDouble(r.correctPct().replace("%", "")))).toList();
        List<ExamReport.QuestionRow> hardest = ranked.stream().limit(5).toList();
        List<ExamReport.QuestionRow> easiest = ranked.stream().sorted(Comparator.comparingDouble((ExamReport.QuestionRow r) -> -Double.parseDouble(r.correctPct().replace("%", "")))).limit(5).toList();

        // ---------------------------------------------------------------- ranking
        List<ExamReport.StudentRow> ranking = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Scored x = scored.get(i);
            int rank = 1;
            for (Scored y : scored) if (y.correct() > x.correct()) rank++;
            McqSubmission s = x.s();
            ranking.add(new ExamReport.StudentRow(rank, s.getStudentName(), s.getRegistrationId(), canonicalDistrict(s.getDistrict()),
                    s.getBatch(), s.getSchool(), x.correct(), x.wrong(), x.blank(), x.correct() + " / " + total, p1(x.pct()),
                    x.seconds() == null ? "-" : clock(x.seconds()), s.getSubmittedAt() == null ? "-" : SHORT.format(s.getSubmittedAt()), x.grade(),
                    TextImages.render(s.getStudentName(), true, 8.3, new java.awt.Color(0x0f172a))));
        }

        // ---------------------------------------------------------------- insights, details, warnings
        List<String> insights = insights(n, total, meanPct, median, best, worst, passCount, districtRows, hardest, easiest, questions, units, secs, allotted, gradeCount[4]);
        List<ExamReport.Pair> highlights = new ArrayList<>();
        if (best != null) highlights.add(new ExamReport.Pair("Top performer", best.s().getStudentName() + "  -  " + p1(best.pct()) + " (" + best.correct() + " / " + total + ")"));
        if (!districtRows.isEmpty()) highlights.add(new ExamReport.Pair("Best district average", districtRows.getFirst().name() + "  -  " + districtRows.getFirst().avgPct()));
        if (!hardest.isEmpty()) highlights.add(new ExamReport.Pair("Hardest question", "Q" + hardest.getFirst().number() + "  -  " + hardest.getFirst().correctPct() + " answered correctly"));
        if (!units.isEmpty()) highlights.add(new ExamReport.Pair("Weakest syllabus unit", units.getFirst().unit() + "  -  " + units.getFirst().correctPct()));
        List<String> warnings = new ArrayList<>();
        if (!exam.hasCompleteAnswerKey()) {
            warnings.add("The answer key is incomplete (" + exam.answerKeyCount() + " of " + total + " questions have a key). Questions without a key are marked wrong for everyone.");
        }
        if (inProgress > 0) warnings.add(inProgress + " paper(s) are still in progress and are not included in this report.");
        if (n == 0) warnings.add("No student has submitted this paper yet, so statistics are not available.");
        if (n > 0 && n < 10) warnings.add("Fewer than 10 submissions: discrimination and group comparisons are not shown.");

        String freeList = IntStream.rangeClosed(1, total).filter(exam::isFreeMark).mapToObj(String::valueOf).collect(Collectors.joining(", "));
        List<ExamReport.Pair> details = new ArrayList<>();
        details.add(new ExamReport.Pair("Examination", exam.getName()));
        details.add(new ExamReport.Pair("Period", exam.getExamMonth() + " " + exam.getExamYear()));
        details.add(new ExamReport.Pair("Paper type", exam.usesQuestionImages() ? "Question-by-question (one image per question)" : "Answer sheet (PDF paper + OMR sheet)"));
        details.add(new ExamReport.Pair("Questions / options", total + " questions, " + options + " options each"));
        details.add(new ExamReport.Pair("Duration", exam.getDurationMinutes() == null ? "No fixed time limit" : exam.getDurationMinutes() + " minutes"));
        details.add(new ExamReport.Pair("Opened", exam.getOpenAt() == null ? "-" : STAMP.format(exam.getOpenAt())));
        details.add(new ExamReport.Pair("Results", exam.isResultsPublished() ? "Released to students" : "Not released yet"));
        details.add(new ExamReport.Pair("Eligible batches", exam.getEligibleBatches().isEmpty() ? "All batches" : String.join(", ", exam.getEligibleBatches())));
        details.add(new ExamReport.Pair("Eligible streams", exam.getEligibleStreams().isEmpty() ? "All streams" : String.join(", ", exam.getEligibleStreams())));
        details.add(new ExamReport.Pair("Answer key", exam.hasCompleteAnswerKey() ? "Complete (" + total + " / " + total + ")" : "Incomplete (" + exam.answerKeyCount() + " / " + total + ")"));
        details.add(new ExamReport.Pair("Free-mark questions", freeList.isEmpty() ? "None" : freeList));
        details.add(new ExamReport.Pair("Marking", "1 mark per correct answer, no negative marking"));
        details.add(new ExamReport.Pair("Pass threshold", p1(PASS_PCT) + " (used for pass rate)"));
        details.add(new ExamReport.Pair("Submissions", n + " submitted" + (inProgress > 0 ? ", " + inProgress + " in progress" : "")));

        ExamReport.Meta metaInfo = new ExamReport.Meta(exam.getName(), exam.getExamMonth() + " " + exam.getExamYear(),
                exam.getEligibleBatches().isEmpty() ? "All batches" : String.join(", ", exam.getEligibleBatches()),
                exam.getEligibleStreams().isEmpty() ? "All streams" : String.join(", ", exam.getEligibleStreams()),
                STAMP.format(Instant.now()), logo, banner, total, n, n > 0, nz(exam.getInstructions()));

        return new ExamReport(metaInfo, summary, histogram, grades, insights, warnings, details, highlights, byBatch, byStream,
                timeStats, timeBuckets, timeline, districtRows, missingDistricts, questions, units, hardest, easiest, ranking);
    }

    // =================================================================== helpers

    private static Integer validSelection(Integer selected, int options) {
        return selected != null && selected >= 1 && selected <= options ? selected : null;
    }

    private static String gradeOf(double pct) { return new String[]{"A", "B", "C", "S", "F"}[indexOfGrade(pct)]; }

    private static int indexOfGrade(double pct) {
        return pct >= 75 ? 0 : pct >= 65 ? 1 : pct >= 50 ? 2 : pct >= 35 ? 3 : 4;
    }

    private static String canonicalDistrict(String raw) {
        String d = blankTo(raw, "Unknown");
        for (String known : DISTRICTS) if (known.equalsIgnoreCase(d)) return known;
        return d;
    }

    private static String blankTo(String v, String fallback) { return v == null || v.isBlank() ? fallback : v.trim(); }
    private static String nz(String v) { return v == null ? "" : v; }
    private static String f1(double v) { return String.format(Locale.ROOT, "%.1f", v); }
    private static String p1(double v) { return String.format(Locale.ROOT, "%.1f%%", v); }

    private static double median(double[] values) {
        if (values.length == 0) return 0;
        double[] copy = values.clone();
        java.util.Arrays.sort(copy);
        int mid = copy.length / 2;
        return copy.length % 2 == 1 ? copy[mid] : (copy[mid - 1] + copy[mid]) / 2;
    }

    private static double std(double[] values, double mean) {
        if (values.length < 2) return 0;
        double sum = 0;
        for (double v : values) sum += (v - mean) * (v - mean);
        return Math.sqrt(sum / values.length);
    }

    /** m:ss (or h:mm:ss) clock text. */
    private static String clock(double seconds) {
        long s = Math.round(seconds);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    private static String students(int count) { return count + (count == 1 ? " student" : " students"); }

    private static String duration(double seconds) {
        long s = Math.round(seconds);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return h + " h " + String.format("%02d", m) + " min";
        if (m > 0) return m + " min " + String.format("%02d", sec) + " s";
        return sec + " s";
    }

    private static List<ExamReport.Band> bands(List<String> labels, int[] counts, int n) {
        int max = Math.max(1, IntStream.of(counts).max().orElse(1));
        List<ExamReport.Band> list = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            list.add(new ExamReport.Band(labels.get(i), counts[i], n == 0 ? "0%" : p1(counts[i] * 100.0 / n), counts[i] * 100 / max));
        }
        return list;
    }

    private static List<ExamReport.Group> groups(List<Scored> scored, java.util.function.Function<Scored, String> key, int limit) {
        Map<String, List<Scored>> map = new LinkedHashMap<>();
        for (Scored x : scored) map.computeIfAbsent(key.apply(x), k -> new ArrayList<>()).add(x);
        int max = map.values().stream().mapToInt(List::size).max().orElse(1);
        int n = scored.size();
        return map.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, List<Scored>>>comparingInt(e -> -e.getValue().size())
                        .thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(e -> new ExamReport.Group(e.getKey(), e.getValue().size(), p1(e.getValue().size() * 100.0 / n),
                        p1(e.getValue().stream().mapToDouble(Scored::pct).average().orElse(0)), e.getValue().size() * 100 / max,
                        TextImages.render(e.getKey(), true, 8, new java.awt.Color(0x1e293b))))
                .toList();
    }

    private static List<ExamReport.Band> timeBuckets(List<Double> secs, double allottedMinutes) {
        if (secs.isEmpty()) return List.of();
        double maxSec = Collections.max(secs);
        double step = allottedMinutes > 0 ? allottedMinutes * 60 / 4 : Math.max(300, Math.ceil(maxSec / 6 / 300) * 300);
        int count = allottedMinutes > 0 ? 4 : (int) Math.max(1, Math.ceil(maxSec / step));
        int[] counts = new int[count];
        for (double v : secs) counts[Math.min(count - 1, (int) (v / step))]++;
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long from = Math.round(i * step / 60), to = Math.round((i + 1) * step / 60);
            labels.add(from + "-" + to + " min");
        }
        return bands(labels, counts, secs.size());
    }

    private static List<ExamReport.Band> timeline(List<McqSubmission> done) {
        Map<LocalDate, Integer> perDay = new TreeMap<>();
        for (McqSubmission s : done) {
            if (s.getSubmittedAt() != null) perDay.merge(s.getSubmittedAt().atZone(ZONE).toLocalDate(), 1, Integer::sum);
        }
        if (perDay.isEmpty()) return List.of();
        List<Map.Entry<LocalDate, Integer>> entries = new ArrayList<>(perDay.entrySet());
        if (entries.size() > 14) entries = entries.subList(entries.size() - 14, entries.size());
        int[] counts = entries.stream().mapToInt(Map.Entry::getValue).toArray();
        List<String> labels = entries.stream().map(e -> DAY.format(e.getKey().atStartOfDay(ZONE))).toList();
        return bands(labels, counts, done.size());
    }

    private static List<ExamReport.UnitRow> unitRows(List<ExamReport.QuestionRow> questions) {
        Map<String, Set<Integer>> unitQuestions = new LinkedHashMap<>();
        for (ExamReport.QuestionRow q : questions) {
            if (q.freeMark()) continue;
            Set<String> seen = new LinkedHashSet<>();
            for (ExamReport.TagRow t : q.tags()) if (!t.unit().isBlank()) seen.add(t.unit());
            for (String u : seen) unitQuestions.computeIfAbsent(u, k -> new LinkedHashSet<>()).add(q.number());
        }
        Map<Integer, ExamReport.QuestionRow> byNumber = questions.stream().collect(Collectors.toMap(ExamReport.QuestionRow::number, r -> r));
        List<ExamReport.UnitRow> rows = new ArrayList<>();
        unitQuestions.forEach((unit, nums) -> {
            double avg = nums.stream().mapToDouble(nm -> Double.parseDouble(byNumber.get(nm).correctPct().replace("%", ""))).average().orElse(0);
            String level = avg >= 70 ? "Strong" : avg >= 40 ? "Developing" : "Needs attention";
            String cls = avg >= 70 ? "easy" : avg >= 40 ? "mod" : "hard";
            rows.add(new ExamReport.UnitRow(unit, nums.stream().map(nm -> "Q" + nm).collect(Collectors.joining(", ")), nums.size(),
                    p1(avg), (int) Math.round(avg), level, cls));
        });
        rows.sort(Comparator.comparingDouble(r -> Double.parseDouble(r.correctPct().replace("%", ""))));
        return rows;
    }

    private static String insight(int q, boolean free, int n, double correctPct, String difficulty, int topWrongOpt, int topWrongCount,
                                  Double disc, double topPct, double bottomPct, Integer avgSec, Integer expected, String verdict) {
        if (free) return "This question was withdrawn by the teacher, so every student received the mark.";
        if (n == 0) return "No submissions yet.";
        StringBuilder sb = new StringBuilder();
        sb.append(p1(correctPct)).append(" of students answered correctly, so the question is rated ").append(difficulty.toLowerCase(Locale.ROOT)).append(". ");
        if (topWrongOpt > 0 && topWrongCount * 100.0 / n >= 15) {
            sb.append("The most common wrong choice was option ").append(topWrongOpt).append(" (").append(p1(topWrongCount * 100.0 / n)).append(") - a likely misconception worth revisiting. ");
        }
        if (disc != null) {
            sb.append("The top group scored ").append(p1(topPct)).append(" against ").append(p1(bottomPct)).append(" for the bottom group (discrimination ")
                    .append(String.format(Locale.ROOT, "%.2f", disc)).append("). ");
        }
        if (avgSec != null && expected != null && !verdict.isEmpty()) {
            sb.append("Students spent ").append(clock(avgSec)).append(" (m:ss) on average against ")
                    .append(clock(expected)).append(" expected (").append(verdict.toLowerCase(Locale.ROOT)).append(").");
        }
        return sb.toString().trim();
    }

    private static List<String> insights(int n, int total, double meanPct, double median, Scored best, Scored worst, int passCount,
                                         List<ExamReport.DistrictRow> districts, List<ExamReport.QuestionRow> hardest,
                                         List<ExamReport.QuestionRow> easiest, List<ExamReport.QuestionRow> questions,
                                         List<ExamReport.UnitRow> units, List<Double> secs, double allotted, int failing) {
        List<String> list = new ArrayList<>();
        if (n == 0) { list.add("No submissions yet - insights will appear once students submit."); return list; }
        list.add(students(n) + " submitted. The average score is " + p1(meanPct) + " (median " + p1(median) + "); the highest is "
                + p1(best.pct()) + " (" + best.s().getStudentName() + ") and the lowest is " + p1(worst.pct()) + ".");
        list.add(passCount + " of " + students(n) + " (" + p1(passCount * 100.0 / n) + ") reached the " + p1(PASS_PCT) + " threshold.");
        if (districts.size() >= 2) {
            ExamReport.DistrictRow top = districts.getFirst(), low = districts.getLast();
            list.add("Best district average: " + top.name() + " (" + top.avgPct() + ", " + students(top.count()) + "); lowest: " + low.name()
                    + " (" + low.avgPct() + ", " + students(low.count()) + "). Districts with very few students should be read with care.");
        } else if (districts.size() == 1) {
            list.add("All students came from " + districts.getFirst().name() + ".");
        }
        if (!hardest.isEmpty()) {
            list.add("Hardest question: Q" + hardest.getFirst().number() + " (" + hardest.getFirst().correctPct() + " correct). Easiest: Q"
                    + easiest.getFirst().number() + " (" + easiest.getFirst().correctPct() + " correct).");
        }
        if (failing > 0) list.add(students(failing) + " scored below 35% (grade F) and may need extra support.");
        List<String> review = questions.stream().filter(r -> !r.flag().isEmpty()).map(r -> "Q" + r.number()).toList();
        if (!review.isEmpty()) list.add("Questions flagged for review: " + String.join(", ", review) + " (see their pages for the reason).");
        List<ExamReport.UnitRow> weak = units.stream().filter(u -> !"easy".equals(u.levelClass())).limit(3).toList();
        if (!weak.isEmpty()) list.add("Syllabus areas that need attention: " + weak.stream().map(u -> u.unit() + " (" + u.correctPct() + ")").collect(Collectors.joining("; ")) + ".");
        if (!secs.isEmpty() && allotted > 0) {
            double avg = secs.stream().mapToDouble(d -> d).average().orElse(0);
            list.add("Students used on average " + duration(avg) + " of the " + (int) allotted + ((int) allotted == 1 ? " minute" : " minutes") + " allowed (" + p1(avg / (allotted * 60) * 100) + ").");
        }
        return list;
    }
}
