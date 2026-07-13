package com.example.pcaExamAnalyze.service;

import com.example.pcaExamAnalyze.domain.McqExam;
import com.example.pcaExamAnalyze.domain.McqBatch;
import com.example.pcaExamAnalyze.domain.McqExamPublicationState;
import com.example.pcaExamAnalyze.domain.McqSubmission;
import com.example.pcaExamAnalyze.domain.McqSubmissionStatus;
import com.example.pcaExamAnalyze.repo.McqExamRepository;
import com.example.pcaExamAnalyze.repo.McqBatchRepository;
import com.example.pcaExamAnalyze.repo.McqSubmissionRepository;
import com.example.pcaExamAnalyze.web.dto.McqExamForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class McqAdminService {

    public static final ZoneId PCA_ZONE = ZoneId.of("Asia/Colombo");
    private static final Instant NO_CLOSE_AT = Instant.parse("9999-12-31T23:59:59Z");
    public static final List<String> STREAMS = List.of("Bio Science", "Physical Science");
    public static final List<String> MONTHS = List.of(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December");

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH).withZone(PCA_ZONE);

    private final McqExamRepository exams;
    private final McqSubmissionRepository submissions;
    private final UserService users;
    private final McqBatchRepository batches;
    private final Object publicExamCacheLock = new Object();
    private volatile PublicExamSnapshot publicExamSnapshot = new PublicExamSnapshot(0, List.of());

    public McqAdminService(McqExamRepository exams, McqSubmissionRepository submissions, UserService users,
                           McqBatchRepository batches) {
        this.exams = exams;
        this.submissions = submissions;
        this.users = users;
        this.batches = batches;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        List<McqExam> allExams = exams.findAllByOrderByCreatedAtDesc();
        List<McqSubmission> allSubmissions = submissions.findAllByOrderByCreatedAtDesc();
        Instant since = Instant.now().minusSeconds(24 * 60 * 60);
        List<McqSubmission> recentSubmissions = allSubmissions.stream()
                .filter(submission -> submission.getSubmittedAt() != null
                        && !submission.getSubmittedAt().isBefore(since))
                .toList();
        Map<Long, List<McqSubmission>> byExam = recentSubmissions.stream()
                .collect(Collectors.groupingBy(submission -> submission.getExam().getId()));
        List<ExamSubmissionCount> submissionCounts = allExams.stream()
                .filter(exam -> byExam.containsKey(exam.getId()))
                .map(exam -> new ExamSubmissionCount(exam.getId(), exam.getName(), byExam.get(exam.getId()).size()))
                .sorted(Comparator.comparingLong(ExamSubmissionCount::count).reversed())
                .toList();
        return new Dashboard(allExams.size(), recentSubmissions.size(), activeBatchRows().size(),
                submissionCounts, allExams.stream().limit(5).map(this::examRow).toList());
    }

    @Transactional(readOnly = true)
    public List<BatchRow> batchRows() {
        return batches.findAllByOrderByNameAsc().stream()
                .map(batch -> new BatchRow(batch.getId(), batch.getName(), batch.isActive())).toList();
    }

    @Transactional(readOnly = true)
    public List<BatchRow> activeBatchRows() {
        return batches.findByActiveTrueOrderByNameAsc().stream()
                .map(batch -> new BatchRow(batch.getId(), batch.getName(), true)).toList();
    }

    @Transactional(readOnly = true)
    public List<String> activeBatchNames() {
        return batches.findByActiveTrueOrderByNameAsc().stream().map(McqBatch::getName).toList();
    }

    @Transactional
    public void addBatch(String name) {
        String cleaned = clean(name);
        if (cleaned.isBlank()) throw new IllegalArgumentException("Batch name is required");
        if (cleaned.length() > 80) throw new IllegalArgumentException("Batch name is too long");
        if (batches.existsByNameIgnoreCase(cleaned)) throw new IllegalArgumentException("Batch already exists");
        batches.save(new McqBatch(cleaned));
    }

    @Transactional
    public void toggleBatch(Long id) {
        McqBatch batch = batches.findById(id).orElseThrow(() -> new IllegalArgumentException("Batch not found"));
        batch.setActive(!batch.isActive());
        batch.setUpdatedAt(Instant.now());
    }

    @Transactional
    public void renameBatch(Long id, String name) {
        McqBatch batch = batches.findById(id).orElseThrow(() -> new IllegalArgumentException("Batch not found"));
        String previousName = batch.getName();
        String cleaned = clean(name);
        if (cleaned.isBlank()) throw new IllegalArgumentException("Batch name is required");
        if (cleaned.length() > 80) throw new IllegalArgumentException("Batch name is too long");
        if (batches.existsByNameIgnoreCaseAndIdNot(cleaned, id)) {
            throw new IllegalArgumentException("Batch already exists");
        }
        batch.setName(cleaned);
        batch.setUpdatedAt(Instant.now());
        for (McqExam exam : exams.findAllByOrderByCreatedAtDesc()) {
            if (exam.getEligibleBatches().remove(previousName)) {
                exam.getEligibleBatches().add(cleaned);
                exam.setUpdatedAt(Instant.now());
            }
        }
        invalidatePublicExamCache();
    }

    @Transactional
    public void deleteBatch(Long id) {
        McqBatch batch = batches.findById(id).orElseThrow(() -> new IllegalArgumentException("Batch not found"));
        boolean inUse = exams.findAllByOrderByCreatedAtDesc().stream()
                .anyMatch(exam -> exam.getEligibleBatches().contains(batch.getName()));
        if (inUse) throw new IllegalArgumentException("This batch is used by an exam. Deactivate it instead.");
        batches.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<ExamRow> examRows() {
        return exams.findAllByOrderByCreatedAtDesc().stream().map(this::examRow).toList();
    }

    @Transactional(readOnly = true)
    public PageResult<ExamRow> examPage(int requestedPage, String search, String batch, String month, Integer year) {
        String needle = clean(search).toLowerCase(Locale.ROOT);
        List<ExamRow> filtered = exams.findAllByOrderByCreatedAtDesc().stream()
                .filter(exam -> needle.isBlank() || exam.getName().toLowerCase(Locale.ROOT).contains(needle))
                .filter(exam -> batch == null || batch.isBlank() || exam.getEligibleBatches().contains(batch))
                .filter(exam -> month == null || month.isBlank() || month.equals(exam.getExamMonth()))
                .filter(exam -> year == null || year.equals(exam.getExamYear()))
                .map(this::examRow)
                .toList();
        return page(filtered, requestedPage, 10);
    }

    @Transactional(readOnly = true)
    public ExamAnalytics examAnalytics(Long examId) {
        McqExam exam = requireExam(examId);
        List<McqSubmission> examSubmissions = submissions.findByExamIdOrderByCreatedAtDesc(examId);
        int[] scoreBands = new int[5];
        for (McqSubmission submission : examSubmissions) {
            if (submission.getScore() == null) continue;
            double percentage = exam.getTotalQuestions() == 0 ? 0
                    : submission.getScore() * 100.0 / exam.getTotalQuestions();
            int band = percentage <= 20 ? 0 : percentage <= 40 ? 1
                    : percentage <= 60 ? 2 : percentage <= 80 ? 3 : 4;
            scoreBands[band]++;
        }
        int maxBand = Math.max(1, IntStream.of(scoreBands).max().orElse(1));
        String[] labels = {"0-20%", "21-40%", "41-60%", "61-80%", "81-100%"};
        List<AnalyticsBar> distribution = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            distribution.add(new AnalyticsBar(labels[i], scoreBands[i], scoreBands[i] * 100 / maxBand));
        }
        List<QuestionPerformance> questions = new ArrayList<>();
        for (int question = 1; question <= exam.getTotalQuestions(); question++) {
            Set<Integer> correctOptions = exam.correctOptions(question);
            int answered = 0;
            int correct = 0;
            for (McqSubmission submission : examSubmissions) {
                Integer selected = submission.getAnswers().get(question);
                if (selected != null) answered++;
                if (selected != null && correctOptions.contains(selected)) correct++;
            }
            int percentage = answered == 0 ? 0 : correct * 100 / answered;
            questions.add(new QuestionPerformance(question, correct, answered, percentage));
        }
        return new ExamAnalytics(examRow(exam), exam.getInstructions(), exam.getDurationMinutes(), distribution,
                questions, examSubmissions.stream().map(this::submissionRow).toList());
    }

    @Transactional(readOnly = true)
    public SubmissionDetail submissionDetail(Long submissionId) {
        McqSubmission submission = submissions.findDetailedById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found"));
        McqExam exam = submission.getExam();
        List<AnswerReview> answers = new ArrayList<>();
        for (int question = 1; question <= exam.getTotalQuestions(); question++) {
            Integer selected = submission.getAnswers().get(question);
            List<Integer> correct = exam.isResultsPublished()
                    ? new ArrayList<>(exam.correctOptions(question)) : List.of();
            String state = selected == null ? "UNANSWERED"
                    : correct.isEmpty() ? "ANSWERED" : correct.contains(selected) ? "CORRECT" : "INCORRECT";
            answers.add(new AnswerReview(question, selected, correct, state));
        }
        return new SubmissionDetail(submission.getId(), exam.getId(), exam.getName(), submission.getReceiptNumber(),
                submission.getStudentName(), submission.getRegistrationId(), submission.getNic(), submission.getEmail(),
                submission.getSchool(), submission.getBatch(), submission.getStream(), submission.getDistrict(),
                format(submission.getStartedAt()), format(submission.getSubmittedAt()), submission.getStatus().name(),
                submission.getScore(), submission.getPercentage(), exam.getTotalQuestions(),
                exam.isResultsPublished(), answers);
    }

    @Transactional(readOnly = true)
    public List<PublicExamRow> publicExamRows() {
        return cachedPublicExamRows();
    }

    @Transactional(readOnly = true)
    public PageResult<PublicExamRow> publicExamPage(int requestedPage, String search, String batch,
                                                    String month, Integer year) {
        String needle = clean(search).toLowerCase(Locale.ROOT);
        List<PublicExamRow> filtered = cachedPublicExamRows().stream()
                .filter(exam -> needle.isBlank() || exam.name().toLowerCase(Locale.ROOT).contains(needle))
                .filter(exam -> batch == null || batch.isBlank() || containsBatch(exam.batches(), batch))
                .filter(exam -> month == null || month.isBlank() || month.equals(exam.month()))
                .filter(exam -> year == null || year.equals(exam.year()))
                .toList();
        return page(filtered, requestedPage, 9);
    }

    @Transactional(readOnly = true)
    public List<Integer> publicExamYears() {
        return cachedPublicExamRows().stream().map(PublicExamRow::year).distinct()
                .sorted(Comparator.reverseOrder()).toList();
    }

    private List<PublicExamRow> cachedPublicExamRows() {
        long now = System.nanoTime();
        PublicExamSnapshot snapshot = publicExamSnapshot;
        if (now < snapshot.expiresAtNanos()) return snapshot.rows();
        synchronized (publicExamCacheLock) {
            snapshot = publicExamSnapshot;
            if (now < snapshot.expiresAtNanos()) return snapshot.rows();
            List<PublicExamRow> rows = exams.findPublishedForStudents().stream()
                    .map(this::publicExamRow).toList();
            publicExamSnapshot = new PublicExamSnapshot(now + 3_000_000_000L, rows);
            return rows;
        }
    }

    private void invalidatePublicExamCache() {
        publicExamSnapshot = new PublicExamSnapshot(0, List.of());
    }

    private static boolean containsBatch(String batches, String selected) {
        if (batches == null || batches.isBlank()) return false;
        return java.util.Arrays.stream(batches.split(","))
                .map(String::trim).anyMatch(selected::equals);
    }

    private PublicExamRow publicExamRow(McqExam exam) {
        return new PublicExamRow(exam.getId(), exam.getSlug(), exam.getName(),
                exam.getExamMonth() + " " + exam.getExamYear(), exam.getExamMonth(), exam.getExamYear(),
                String.join(", ", exam.getEligibleBatches()), format(exam.getOpenAt()), format(exam.getCloseAt()),
                exam.getDurationMinutes(), exam.getTotalQuestions(), status(exam));
    }

    @Transactional(readOnly = true)
    public McqExam requireExam(Long id) {
        return exams.findById(id).orElseThrow(() -> new IllegalArgumentException("Exam not found"));
    }

    @Transactional(readOnly = true)
    public McqExamForm formFor(Long id) {
        McqExam exam = requireExam(id);
        McqExamForm form = new McqExamForm();
        form.setName(exam.getName());
        form.setBatch(exam.getEligibleBatches().stream().findFirst().orElse(""));
        form.setTotalQuestions(exam.getTotalQuestions());
        form.setExamYear(exam.getExamYear());
        form.setExamMonth(exam.getExamMonth());
        form.setPaperDriveUrl(exam.getPaperDriveUrl());
        form.setOpenAt(LocalDateTime.ofInstant(exam.getOpenAt(), PCA_ZONE));
        form.setCloseAt(LocalDateTime.ofInstant(exam.getCloseAt(), PCA_ZONE));
        form.setResultReleaseAt(LocalDateTime.ofInstant(exam.getResultReleaseAt(), PCA_ZONE));
        form.setInstructions(exam.getInstructions());
        form.setDurationMinutes(exam.getDurationMinutes());
        form.setAllowResubmission(exam.isAllowResubmission());
        form.setAnswers(new LinkedHashMap<>(exam.correctAnswerOptions()));
        return form;
    }

    public void validateForm(McqExamForm form, BindingResult binding) {
        if (form.getBatch() != null && !form.getBatch().isBlank() && !activeBatchNames().contains(form.getBatch())) {
            binding.rejectValue("batch", "invalid", "Select a valid batch");
        }
        if (form.getExamMonth() != null && !MONTHS.contains(form.getExamMonth())) {
            binding.rejectValue("examMonth", "invalid", "Select a valid month");
        }
        normalizeAnswers(form);
    }

    @Transactional
    public McqExam save(Long id, McqExamForm form, boolean publish, String username) {
        McqExam exam = id == null ? new McqExam() : requireExam(id);
        exam.setName(clean(form.getName()));
        if (exam.getSlug() == null || exam.getSlug().isBlank()) {
            exam.setSlug(uniqueSlug(exam.getName(), exam.getId()));
        }
        exam.setEligibleBatches(new LinkedHashSet<>(List.of(form.getBatch())));
        // Stream is not an eligibility restriction. Every supported stream can access
        // an exam when the student's batch matches.
        exam.setEligibleStreams(new LinkedHashSet<>(STREAMS));
        exam.setExamYear(form.getExamYear());
        exam.setExamMonth(form.getExamMonth());
        exam.setPaperDriveUrl(clean(form.getPaperDriveUrl()));
        exam.setOpenAt(form.getOpenAt().atZone(PCA_ZONE).toInstant());
        // Student papers stay open permanently after openAt. Keep a far-future
        // internal value for compatibility with existing databases/rows.
        exam.setCloseAt(NO_CLOSE_AT);
        exam.setResultReleaseAt(form.getResultReleaseAt().atZone(PCA_ZONE).toInstant());
        exam.setInstructions(clean(form.getInstructions()));
        exam.setDurationMinutes(form.getDurationMinutes());
        exam.setPassMark(null);
        exam.setAllowResubmission(form.isAllowResubmission());
        exam.setTotalQuestions(form.getTotalQuestions());
        exam.setOptionsPerQuestion(5);
        exam.setCorrectAnswerOptions(form.getAnswers());
        exam.setPublicationState(publish ? McqExamPublicationState.PUBLISHED : McqExamPublicationState.DRAFT);
        exam.setUpdatedAt(Instant.now());
        if (exam.getCreatedBy() == null) exam.setCreatedBy(users.requireByUsername(username));
        McqExam saved = exams.save(exam);
        invalidatePublicExamCache();
        return saved;
    }

    @Transactional
    public void archive(Long id) {
        McqExam exam = requireExam(id);
        exam.setPublicationState(McqExamPublicationState.ARCHIVED);
        exam.setArchivedAt(Instant.now());
        exam.setUpdatedAt(Instant.now());
        invalidatePublicExamCache();
    }

    @Transactional
    public void deleteExam(Long id) {
        McqExam exam = requireExam(id);
        List<McqSubmission> relatedSubmissions = submissions.findByExamIdOrderByCreatedAtDesc(id);
        if (!relatedSubmissions.isEmpty()) {
            submissions.deleteAll(relatedSubmissions);
            submissions.flush();
        }
        exams.delete(exam);
        invalidatePublicExamCache();
    }

    @Transactional
    public void deleteSubmission(Long id) {
        McqSubmission submission = submissions.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found"));
        submissions.delete(submission);
    }

    @Transactional
    public void releaseResults(Long id) {
        McqExam exam = requireExam(id);
        if (!exam.hasCompleteAnswerKey()) {
            throw new IllegalStateException("Complete all " + exam.getTotalQuestions()
                    + " answer-key entries before releasing results");
        }
        exam.setResultsPublished(true);
        if (exam.getResultReleaseAt().isAfter(Instant.now())) exam.setResultReleaseAt(Instant.now());
        exam.setUpdatedAt(Instant.now());
        invalidatePublicExamCache();
    }

    @Transactional
    public void unpublishResults(Long id) {
        McqExam exam = requireExam(id);
        exam.setResultsPublished(false);
        exam.setUpdatedAt(Instant.now());
        invalidatePublicExamCache();
    }

    @Transactional(readOnly = true)
    public List<SubmissionRow> submissionRows(Long examId, String search) {
        List<McqSubmission> source = examId == null
                ? submissions.findAllByOrderByCreatedAtDesc()
                : submissions.findByExamIdOrderByCreatedAtDesc(examId);
        String needle = clean(search).toLowerCase(Locale.ROOT);
        return source.stream()
                .filter(s -> needle.isBlank()
                        || s.getStudentName().toLowerCase(Locale.ROOT).contains(needle)
                        || s.getRegistrationId().toLowerCase(Locale.ROOT).contains(needle))
                .map(this::submissionRow).toList();
    }

    @Transactional(readOnly = true)
    public PageResult<SubmissionRow> submissionPage(Long examId, String search, LocalDate from,
                                                     LocalDate to, int requestedPage) {
        List<McqSubmission> source = examId == null
                ? submissions.findAllByOrderByCreatedAtDesc()
                : submissions.findByExamIdOrderByCreatedAtDesc(examId);
        String needle = clean(search).toLowerCase(Locale.ROOT);
        List<SubmissionRow> filtered = source.stream()
                .filter(s -> needle.isBlank()
                        || s.getStudentName().toLowerCase(Locale.ROOT).contains(needle)
                        || s.getRegistrationId().toLowerCase(Locale.ROOT).contains(needle))
                .filter(s -> dateMatches(s.getSubmittedAt(), from, to))
                .map(this::submissionRow)
                .toList();
        return page(filtered, requestedPage, 20);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(Long examId) {
        List<McqSubmission> source = examId == null
                ? submissions.findAllByOrderByCreatedAtDesc()
                : submissions.findByExamIdOrderByCreatedAtDesc(examId);
        StringBuilder csv = new StringBuilder("Receipt,Exam,Student,Registration ID,NIC,Email,Batch,School,Stream,District,Submitted,Status,Score\r\n");
        for (McqSubmission s : source) {
            csv.append(csv(s.getReceiptNumber())).append(',')
                    .append(csv(s.getExam().getName())).append(',')
                    .append(csv(s.getStudentName())).append(',')
                    .append(csv(s.getRegistrationId())).append(',')
                    .append(csv(s.getNic())).append(',')
                    .append(csv(s.getEmail())).append(',')
                    .append(csv(s.getBatch())).append(',')
                    .append(csv(s.getSchool())).append(',')
                    .append(csv(s.getStream())).append(',')
                    .append(csv(s.getDistrict())).append(',')
                    .append(csv(format(s.getSubmittedAt()))).append(',')
                    .append(csv(s.getStatus().name())).append(',')
                    .append(s.getScore() == null ? "" : s.getScore()).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public List<Integer> questionNumbers() {
        return IntStream.rangeClosed(1, 50).boxed().toList();
    }

    public List<Integer> optionNumbers() {
        return IntStream.rangeClosed(1, 5).boxed().toList();
    }

    private ExamRow examRow(McqExam exam) {
        return new ExamRow(exam.getId(), exam.getSlug(), exam.getName(), String.join(", ", exam.getEligibleBatches()),
                format(exam.getOpenAt()), format(exam.getCloseAt()),
                format(exam.getResultReleaseAt()), submissions.countByExamId(exam.getId()), exam.answerKeyCount(),
                exam.getTotalQuestions(), status(exam), exam.isResultsPublished(), exam.getPaperDriveUrl());
    }

    private SubmissionRow submissionRow(McqSubmission submission) {
        String score = submission.getScore() == null ? "-"
                : submission.getScore() + " / " + submission.getExam().getTotalQuestions();
        return new SubmissionRow(submission.getId(), submission.getReceiptNumber(), submission.getExam().getName(),
                submission.getStudentName(), submission.getRegistrationId(), submission.getNic(),
                submission.getEmail(), submission.getBatch(), submission.getSchool(), submission.getStream(),
                submission.getDistrict(), format(submission.getSubmittedAt()), submission.getStatus().name(), score);
    }

    private String status(McqExam exam) {
        if (exam.getPublicationState() == McqExamPublicationState.ARCHIVED) return "ARCHIVED";
        if (exam.getPublicationState() == McqExamPublicationState.DRAFT) return "DRAFT";
        Instant now = Instant.now();
        if (exam.isResultsPublished() && !now.isBefore(exam.getResultReleaseAt())) return "RESULT RELEASED";
        if (now.isBefore(exam.getOpenAt())) return "SCHEDULED";
        return "OPEN";
    }

    private String uniqueSlug(String name, Long currentId) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (base.isBlank()) base = "pca-exam";
        String candidate = base;
        int suffix = 2;
        while (currentId == null ? exams.existsBySlug(candidate) : exams.existsBySlugAndIdNot(candidate, currentId)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static void normalizeAnswers(McqExamForm form) {
        Map<Integer, List<Integer>> normalized = new LinkedHashMap<>();
        int totalQuestions = form.getTotalQuestions() == null ? 50 : Math.max(0, Math.min(50, form.getTotalQuestions()));
        if (form.getAnswers() != null) {
            form.getAnswers().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                if (entry.getKey() == null || entry.getValue() == null
                        || entry.getKey() < 1 || entry.getKey() > totalQuestions) return;
                List<Integer> valid = entry.getValue().stream().filter(java.util.Objects::nonNull)
                        .filter(option -> option >= 1 && option <= 5).distinct().sorted().toList();
                if (!valid.isEmpty()) normalized.put(entry.getKey(), valid);
            });
        }
        form.setAnswers(normalized);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String format(Instant instant) {
        return instant == null ? "-" : DISPLAY_TIME.format(instant);
    }

    public static String formatPublic(Instant instant) {
        return format(instant);
    }

    private static boolean dateMatches(Instant instant, LocalDate from, LocalDate to) {
        if (from == null && to == null) return true;
        if (instant == null) return false;
        LocalDate date = instant.atZone(PCA_ZONE).toLocalDate();
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    private static <T> PageResult<T> page(List<T> rows, int requestedPage, int pageSize) {
        int totalPages = rows.isEmpty() ? 0 : (rows.size() + pageSize - 1) / pageSize;
        int currentPage = totalPages == 0 ? 0 : Math.max(0, Math.min(requestedPage, totalPages - 1));
        int start = Math.min(currentPage * pageSize, rows.size());
        int end = Math.min(start + pageSize, rows.size());
        return new PageResult<>(rows.subList(start, end), currentPage, totalPages, rows.size(),
                currentPage > 0, currentPage + 1 < totalPages);
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    public record Dashboard(long totalExams, long last24Submissions, long activeBatches,
                            List<ExamSubmissionCount> submissionCounts, List<ExamRow> recentExams) {}
    public record ExamSubmissionCount(Long examId, String examName, long count) {}
    public record BatchRow(Long id, String name, boolean active) {}
    public record PageResult<T>(List<T> items, int page, int totalPages, long totalItems,
                                boolean hasPrevious, boolean hasNext) {}
    public record AnalyticsBar(String label, int value, int height) {}
    public record QuestionPerformance(int question, int correct, int answered, int percentage) {}
    public record ExamAnalytics(ExamRow exam, String instructions, Integer durationMinutes,
                                List<AnalyticsBar> scoreDistribution, List<QuestionPerformance> questions,
                                List<SubmissionRow> submissions) {}
    public record AnswerReview(int question, Integer selectedOption, List<Integer> correctOptions, String state) {}
    public record SubmissionDetail(Long id, Long examId, String examName, String receipt, String studentName,
                                   String registrationId, String nic, String email, String school, String batch,
                                   String stream, String district, String started, String submitted, String status,
                                   Integer score, Double percentage, Integer totalQuestions,
                                   boolean resultsPublished, List<AnswerReview> answers) {}
    public record ExamRow(Long id, String slug, String name, String batches, String opens, String closes,
                          String resultRelease, long submissions, int keyCount, int totalQuestions,
                          String status, boolean resultsPublished, String paperUrl) {}
    public record PublicExamRow(Long id, String slug, String name, String period, String month, Integer year,
                                String batches, String opens, String closes, Integer durationMinutes,
                                Integer totalQuestions, String status) {}
    private record PublicExamSnapshot(long expiresAtNanos, List<PublicExamRow> rows) {}
    public record SubmissionRow(Long id, String receipt, String examName, String studentName,
                                String registrationId, String nic, String email, String batch,
                                String school, String stream, String district, String submitted,
                                String status, String score) {}
}
