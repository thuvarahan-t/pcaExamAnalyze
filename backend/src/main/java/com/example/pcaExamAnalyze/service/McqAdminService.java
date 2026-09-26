package com.example.pcaExamAnalyze.service;

import com.example.pcaExamAnalyze.domain.McqExam;
import com.example.pcaExamAnalyze.domain.McqBatch;
import com.example.pcaExamAnalyze.domain.McqExamPublicationState;
import com.example.pcaExamAnalyze.domain.McqExamQuestion;
import com.example.pcaExamAnalyze.repo.McqQuestionMeta;
import com.example.pcaExamAnalyze.domain.McqSheetType;
import com.example.pcaExamAnalyze.domain.McqSubmission;
import com.example.pcaExamAnalyze.domain.McqSubmissionStatus;
import com.example.pcaExamAnalyze.repo.McqExamRepository;
import com.example.pcaExamAnalyze.repo.McqBatchRepository;
import com.example.pcaExamAnalyze.repo.McqSubmissionRepository;
import com.example.pcaExamAnalyze.web.dto.McqExamForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

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
import java.util.Optional;
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
    private final McqExamQuestionService questionImages;
    private final Object publicExamCacheLock = new Object();
    private volatile PublicExamSnapshot publicExamSnapshot = new PublicExamSnapshot(0, List.of());
    private volatile BatchSnapshot batchSnapshot = new BatchSnapshot(0, List.of(), List.of());
    private volatile ExamSnapshot examSnapshot = new ExamSnapshot(0, List.of());
    private volatile DashboardSnapshot dashboardSnapshot = new DashboardSnapshot(0, null);

    public McqAdminService(McqExamRepository exams, McqSubmissionRepository submissions, UserService users,
                           McqBatchRepository batches, McqExamQuestionService questionImages) {
        this.exams = exams;
        this.questionImages = questionImages;
        this.submissions = submissions;
        this.users = users;
        this.batches = batches;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        long now = System.nanoTime();
        DashboardSnapshot snapshot = dashboardSnapshot;
        if (now < snapshot.expiresAtNanos()) return snapshot.dashboard();
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
        Dashboard loaded = new Dashboard(allExams.size(), recentSubmissions.size(), activeBatchRows().size(),
                submissionCounts, allExams.stream().limit(5).map(exam -> examRow(exam, submissionCounts())).toList());
        dashboardSnapshot = new DashboardSnapshot(now + 5_000_000_000L, loaded);
        return loaded;
    }

    @Transactional(readOnly = true)
    public List<BatchRow> batchRows() {
        return cachedBatchSnapshot().batches();
    }

    @Transactional(readOnly = true)
    public List<BatchRow> activeBatchRows() {
        return cachedBatchSnapshot().batches().stream().filter(BatchRow::active).toList();
    }

    @Transactional(readOnly = true)
    public List<String> activeBatchNames() {
        return cachedBatchSnapshot().activeBatchNames();
    }

    @Transactional
    public void addBatch(String name) {
        String cleaned = clean(name);
        if (cleaned.isBlank()) throw new IllegalArgumentException("Batch name is required");
        if (cleaned.length() > 80) throw new IllegalArgumentException("Batch name is too long");
        if (batches.existsByNameIgnoreCase(cleaned)) throw new IllegalArgumentException("Batch already exists");
        batches.save(new McqBatch(cleaned));
        invalidateAdminCache();
    }

    @Transactional
    public void toggleBatch(Long id) {
        McqBatch batch = batches.findById(id).orElseThrow(() -> new IllegalArgumentException("Batch not found"));
        batch.setActive(!batch.isActive());
        batch.setUpdatedAt(Instant.now());
        invalidateAdminCache();
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
        invalidateAdminCache();
    }

    @Transactional
    public void deleteBatch(Long id) {
        McqBatch batch = batches.findById(id).orElseThrow(() -> new IllegalArgumentException("Batch not found"));
        boolean inUse = exams.findAllByOrderByCreatedAtDesc().stream()
                .anyMatch(exam -> exam.getEligibleBatches().contains(batch.getName()));
        if (inUse) throw new IllegalArgumentException("This batch is used by an exam. Deactivate it instead.");
        batches.deleteById(id);
        invalidateAdminCache();
    }

    @Transactional(readOnly = true)
    public List<ExamRow> examRows() {
        long now = System.nanoTime();
        ExamSnapshot snapshot = examSnapshot;
        if (now < snapshot.expiresAtNanos()) return snapshot.exams();
        Map<Long, Long> counts = submissionCounts();
        List<ExamRow> loaded = exams.findAllByOrderByCreatedAtDesc().stream().map(exam -> examRow(exam, counts)).toList();
        examSnapshot = new ExamSnapshot(now + 30_000_000_000L, loaded);
        return loaded;
    }

    private BatchSnapshot cachedBatchSnapshot() {
        long now = System.nanoTime();
        BatchSnapshot snapshot = batchSnapshot;
        if (now < snapshot.expiresAtNanos()) return snapshot;
        List<BatchRow> batchRows = batches.findAllByOrderByNameAsc().stream()
                .map(batch -> new BatchRow(batch.getId(), batch.getName(), batch.isActive())).toList();
        List<String> activeNames = batchRows.stream().filter(BatchRow::active).map(BatchRow::name).toList();
        snapshot = new BatchSnapshot(now + 300_000_000_000L, batchRows, activeNames);
        batchSnapshot = snapshot;
        return snapshot;
    }

    private void invalidateAdminCache() {
        batchSnapshot = new BatchSnapshot(0, List.of(), List.of());
        examSnapshot = new ExamSnapshot(0, List.of());
        dashboardSnapshot = new DashboardSnapshot(0, null);
    }

    @Transactional(readOnly = true)
    public PageResult<ExamRow> examPage(int requestedPage, String search, String batch, String month, Integer year) {
        String needle = clean(search).toLowerCase(Locale.ROOT);
        List<McqExam> filtered = exams.findAllByOrderByCreatedAtDesc().stream()
                .filter(exam -> needle.isBlank() || exam.getName().toLowerCase(Locale.ROOT).contains(needle))
                .filter(exam -> batch == null || batch.isBlank() || exam.getEligibleBatches().contains(batch))
                .filter(exam -> month == null || month.isBlank() || month.equals(exam.getExamMonth()))
                .filter(exam -> year == null || year.equals(exam.getExamYear()))
                .toList();
        PageResult<McqExam> examPage = page(filtered, requestedPage, 10);
        Map<Long, Long> counts = submissionCounts();
        return new PageResult<>(examPage.items().stream().map(exam -> examRow(exam, counts)).toList(), examPage.page(),
                examPage.totalPages(), examPage.totalItems(), examPage.hasPrevious(), examPage.hasNext());
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
        Map<Integer, McqQuestionMeta> meta = exam.usesQuestionImages() ? questionImages.meta(examId) : Map.of();
        Map<Integer, List<McqExamQuestionService.TagView>> tagMap =
                exam.usesQuestionImages() ? questionImages.tagsByExam(examId) : Map.of();
        for (int question = 1; question <= exam.getTotalQuestions(); question++) {
            Set<Integer> correctOptions = exam.correctOptions(question);
            int answered = 0;
            int correct = 0;
            int timed = 0;
            long totalSeconds = 0;
            for (McqSubmission submission : examSubmissions) {
                Integer selected = submission.getAnswers().get(question);
                if (selected != null || exam.isFreeMark(question)) answered++;
                if (exam.isFreeMark(question) || selected != null && correctOptions.contains(selected)) correct++;
                Integer seconds = exam.usesQuestionImages() ? submission.getQuestionTimes().get(question) : null;
                if (seconds != null && seconds > 0) {
                    timed++;
                    totalSeconds += seconds;
                }
            }
            int percentage = answered == 0 ? 0 : correct * 100 / answered;
            McqQuestionMeta m = meta.get(question);
            Integer expected = m == null ? null : m.timeSeconds();
            Integer average = timed == 0 ? null : (int) Math.round(totalSeconds / (double) timed);
            questions.add(new QuestionPerformance(question, correct, answered, percentage,
                    m == null ? null : m.weight(), unitsLabel(tagMap.get(question), m),
                    expected, average, timeVerdict(average, expected)));
        }
        return new ExamAnalytics(examRow(exam), exam.getInstructions(), exam.getDurationMinutes(), distribution,
                questions, examSubmissions.stream().map(this::submissionRow).toList());
    }

    @Transactional(readOnly = true)
    public SubmissionDetail submissionDetail(Long submissionId) {
        McqSubmission submission = submissions.findDetailedById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found"));
        McqExam exam = submission.getExam();
        Map<Integer, McqQuestionMeta> meta = exam.usesQuestionImages() ? questionImages.meta(exam.getId()) : Map.of();
        List<AnswerReview> answers = new ArrayList<>();
        for (int question = 1; question <= exam.getTotalQuestions(); question++) {
            Integer selected = submission.getAnswers().get(question);
            List<Integer> correct = exam.isResultsPublished()
                    ? new ArrayList<>(exam.correctOptions(question)) : List.of();
            String state = exam.isResultsPublished() && exam.isFreeMark(question) ? "FREE_MARK" : selected == null ? "UNANSWERED"
                    : correct.isEmpty() ? "ANSWERED" : correct.contains(selected) ? "CORRECT" : "INCORRECT";
            Integer seconds = exam.usesQuestionImages() ? submission.getQuestionTimes().get(question) : null;
            McqQuestionMeta m = meta.get(question);
            Integer expected = m == null ? null : m.timeSeconds();
            answers.add(new AnswerReview(question, selected, correct, state, seconds, expected,
                    timeVerdict(seconds, expected)));
        }
        return new SubmissionDetail(submission.getId(), exam.getId(), exam.getName(), submission.getReceiptNumber(),
                submission.getStudentName(), submission.getRegistrationId(), submission.getNic(), submission.getEmail(),
                submission.getSchool(), submission.getBatch(), submission.getStream(), submission.getDistrict(),
                format(submission.getStartedAt()), format(submission.getSubmittedAt()), submission.getStatus().name(),
                submission.getScore(), submission.getPercentage(), exam.getTotalQuestions(),
                exam.isResultsPublished(), answers, exam.usesQuestionImages());
    }

    /** Unit names from the question's syllabus tags; older questions fall back to typed competencies. */
    private static String unitsLabel(List<McqExamQuestionService.TagView> tags, McqQuestionMeta meta) {
        if (tags != null && !tags.isEmpty()) {
            return tags.stream().map(t -> t.levelName().isEmpty() ? t.unitName() : t.unitName() + " (" + t.levelName() + ")")
                    .distinct().collect(Collectors.joining(", "));
        }
        return meta == null ? "" : String.join(", ", meta.unitList());
    }

    @Transactional(readOnly = true)
    public Map<Integer, List<McqExamQuestionService.TagView>> questionTagsByExam(Long examId) {
        requireExam(examId);
        return questionImages.tagsByExam(examId);
    }

    /** FAST / ON_TIME / SLOW against the teacher's expected time (within 25% counts as on time). */
    private static String timeVerdict(Integer seconds, Integer expected) {
        if (seconds == null || seconds <= 0 || expected == null || expected <= 0) return "";
        if (seconds > expected * 1.25) return "SLOW";
        if (seconds < expected * 0.75) return "FAST";
        return "ON_TIME";
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
        invalidateAdminCache();
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

    /** Exam without eagerly joined collections: for hot paths that touch few of them. */
    private McqExam requireExamLite(Long id) {
        return exams.findPlainById(id).orElseThrow(() -> new IllegalArgumentException("Exam not found"));
    }

    @Transactional(readOnly = true)
    public McqExam requireExam(Long id) {
        return exams.findPlainById(id).orElseThrow(() -> new IllegalArgumentException("Exam not found"));
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
        if (exam.getResultReleaseAt() != null) form.setResultReleaseAt(LocalDateTime.ofInstant(exam.getResultReleaseAt(), PCA_ZONE));
        form.setInstructions(exam.getInstructions());
        form.setDurationMinutes(exam.getDurationMinutes());
        form.setAllowResubmission(exam.isAllowResubmission());
        form.setSheetType(exam.effectiveSheetType());
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
        if (form.getSheetType() != McqSheetType.QUESTION_IMAGES
                && (form.getPaperDriveUrl() == null || form.getPaperDriveUrl().isBlank())) {
            binding.rejectValue("paperDriveUrl", "required", "Google Drive PDF link is required");
        }
        normalizeAnswers(form);
    }

    /** Question-editor rows for an image-sheet exam (1..totalQuestions, metadata without bytes). */
    @Transactional(readOnly = true)
    public QuestionEditor questionEditor(Long id) {
        McqExam exam = requireExamLite(id);
        Map<Integer, McqQuestionMeta> meta = questionImages.meta(id);
        Map<Integer, List<Long>> supporting = questionImages.subImageIds(id);
        List<EditorQuestion> rows = new ArrayList<>();
        for (int q = 1; q <= exam.getTotalQuestions(); q++) {
            McqQuestionMeta m = meta.get(q);
            String correct = exam.correctOptions(q).stream().map(String::valueOf).collect(Collectors.joining(","));
            rows.add(m == null
                    ? new EditorQuestion(q, null, correct, null, null, List.of(), List.of(), List.of(), List.of(), exam.isFreeMark(q), supporting.getOrDefault(q, List.of()))
                    : new EditorQuestion(q, m.imageVersion(), correct, m.weight(), m.timeSeconds(),
                    m.unitList(), m.competencyLevelList(), m.contentList(), m.outcomeList(), exam.isFreeMark(q), supporting.getOrDefault(q, List.of())));
        }
        return new QuestionEditor(examRow(exam), exam.usesQuestionImages(),
                exam.getPublicationState() == McqExamPublicationState.PUBLISHED, rows);
    }

    /** Saves one question from the editor, including its accepted answer(s) in the exam's answer key. */
    @Transactional
    public EditorSave saveEditorQuestion(Long id, int question, List<Integer> correctOptions,
                                         McqExamQuestionService.QuestionEdit edit,
                                         MultipartFile image, boolean removeImage, boolean freeMark,
                                         List<MultipartFile> subImages, List<Long> removeSubImages) {
        McqExam exam = requireExamLite(id);
        McqExamQuestion saved = questionImages.save(exam, question, edit, image, removeImage);
        boolean keyChanged = !exam.correctOptions(question).equals(new java.util.LinkedHashSet<>(correctOptions))
                || exam.isFreeMark(question) != freeMark;
        exam.setCorrectOptionsFor(question, correctOptions);
        exam.setFreeMark(question, freeMark);
        questionImages.saveSubImages(id, question, subImages, removeSubImages);
        exam.setUpdatedAt(Instant.now());
        if (keyChanged) rescoreSubmissions(exam);
        String correct = exam.correctOptions(question).stream().map(String::valueOf).collect(Collectors.joining(","));
        invalidatePublicExamCache();
        EditorQuestion view = new EditorQuestion(question, saved.hasImage() ? saved.getImageUpdatedAt().toEpochMilli() : null,
                correct, saved.getWeight(), saved.getTimeSeconds(), McqExamQuestion.splitList(saved.getUnits()),
                McqExamQuestion.splitList(saved.getCompetencyLevels()), McqExamQuestion.splitList(saved.getContents()), McqExamQuestion.splitList(saved.getLearningOutcomes()),
                freeMark, questionImages.subImageIds(id).getOrDefault(question, List.of()));
        return new EditorSave(view, questionImages.tagsOf(saved.getId()));
    }

    /** Question numbers that still have no image; always empty for the classic answer sheet. */
    @Transactional(readOnly = true)
    public List<Integer> missingQuestionImages(Long id) {
        McqExam exam = requireExamLite(id);
        return exam.usesQuestionImages() ? questionImages.missingImages(id, exam.getTotalQuestions()) : List.of();
    }

    /** Schedules a saved exam; an image-sheet exam with missing images stays a draft. */
    @Transactional
    public List<Integer> schedule(Long id) {
        McqExam exam = requireExamLite(id);
        List<Integer> missing = missingQuestionImages(id);
        if (missing.isEmpty()) {
            exam.setPublicationState(McqExamPublicationState.PUBLISHED);
            exam.setUpdatedAt(Instant.now());
            invalidatePublicExamCache();
        }
        return missing;
    }

    @Transactional(readOnly = true)
    public Optional<McqExamQuestion> questionImage(Long id, int question) {
        return questionImages.findImage(id, question);
    }

    /**
     * Saves the exam. When scheduling an image-sheet exam that still has questions without
     * an image, everything is saved but the exam stays a draft; the result lists what is missing.
     */
    @Transactional
    public SaveResult save(Long id, McqExamForm form, boolean publish, String username) {
        McqExam exam = id == null ? new McqExam() : requireExam(id);
        exam.setName(clean(form.getName()));
        if (exam.getSlug() == null || exam.getSlug().isBlank()) {
            exam.setSlug(uniqueSlug(exam.getName(), exam.getId()));
        }
        exam.getEligibleBatches().retainAll(Set.of(form.getBatch()));
        exam.getEligibleBatches().add(form.getBatch());
        // Stream is not an eligibility restriction. Every supported stream can access
        // an exam when the student's batch matches.
        exam.getEligibleStreams().retainAll(STREAMS);
        exam.getEligibleStreams().addAll(STREAMS);
        exam.setExamYear(form.getExamYear());
        exam.setExamMonth(form.getExamMonth());
        exam.setPaperDriveUrl(clean(form.getPaperDriveUrl()));
        exam.setOpenAt(form.getOpenAt().atZone(PCA_ZONE).toInstant());
        // Student papers stay open permanently after openAt. Keep a far-future
        // internal value for compatibility with existing databases/rows.
        exam.setCloseAt(NO_CLOSE_AT);
        // Results are released only by the Release button; this column just records when.
        if (exam.getResultReleaseAt() == null) exam.setResultReleaseAt(NO_CLOSE_AT);
        exam.setInstructions(clean(form.getInstructions()));
        exam.setDurationMinutes(form.getDurationMinutes());
        exam.setPassMark(null);
        exam.setAllowResubmission(form.isAllowResubmission());
        exam.setTotalQuestions(form.getTotalQuestions());
        exam.setOptionsPerQuestion(5);
        exam.setSheetType(form.getSheetType() == null ? McqSheetType.ANSWER_SHEET : form.getSheetType());
        // Question-by-question exams keep their answer key in the Question Editor; the form's
        // grid is hidden for them, so it must not overwrite what the editor saved.
        if (!exam.usesQuestionImages()) exam.setCorrectAnswerOptions(form.getAnswers());
        List<Integer> missing = !exam.usesQuestionImages() ? List.of()
                : exam.getId() == null ? IntStream.rangeClosed(1, exam.getTotalQuestions()).boxed().toList()
                : questionImages.missingImages(exam.getId(), exam.getTotalQuestions());
        boolean published = publish && missing.isEmpty();
        exam.setPublicationState(published ? McqExamPublicationState.PUBLISHED : McqExamPublicationState.DRAFT);
        exam.setUpdatedAt(Instant.now());
        if (exam.getCreatedBy() == null) exam.setCreatedBy(users.requireByUsername(username));
        McqExam saved = exams.save(exam);
        if (saved.isResultsPublished()) rescoreSubmissions(saved);
        invalidatePublicExamCache();
        return new SaveResult(saved, published, missing);
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
        questionImages.deleteForExam(id);
        exams.delete(exam);
        invalidatePublicExamCache();
    }

    @Transactional
    public void deleteSubmission(Long id) {
        McqSubmission submission = submissions.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found"));
        submissions.delete(submission);
        invalidateAdminCache();
    }

    @Transactional
    public void releaseResults(Long id) {
        McqExam exam = requireExam(id);
        if (!exam.hasCompleteAnswerKey()) {
            // Answers may be filled in after scheduling, but every question needs one before release.
            String missing = IntStream.rangeClosed(1, exam.getTotalQuestions())
                    .filter(q -> !exam.isFreeMark(q) && exam.correctOptions(q).isEmpty())
                    .mapToObj(q -> "Q" + q).collect(Collectors.joining(", "));
            throw new IllegalStateException("Set the correct answer before releasing results for: " + missing
                    + (exam.usesQuestionImages() ? " (Question Editor)" : " (Edit exam → Answer Key)"));
        }
        exam.setResultsPublished(true);
        if (exam.getResultReleaseAt().isAfter(Instant.now())) exam.setResultReleaseAt(Instant.now());
        exam.setUpdatedAt(Instant.now());
        rescoreSubmissions(exam);
        invalidatePublicExamCache();
    }

    /**
     * Re-marks every submitted paper against the current answer key. Papers submitted before the
     * key was complete (or before it was corrected) would otherwise keep an empty or stale score.
     */
    private void rescoreSubmissions(McqExam exam) {
        if (!exam.hasCompleteAnswerKey()) return;
        for (McqSubmission submission : submissions.findByExamIdOrderByCreatedAtDesc(exam.getId())) {
            if (submission.getStatus() != McqSubmissionStatus.SUBMITTED) continue;
            McqScoring.score(exam, submission);
            submission.setUpdatedAt(Instant.now());
        }
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

    private Map<Long, Long> submissionCounts() {
        Map<Long, Long> counts = new java.util.HashMap<>();
        submissions.countAllByExam().forEach(row -> counts.put(row.getExamId(), row.getTotal()));
        return counts;
    }

    private ExamRow examRow(McqExam exam) {
        return examRow(exam, Map.of(exam.getId(), submissions.countByExamId(exam.getId())));
    }

    private ExamRow examRow(McqExam exam, Map<Long, Long> counts) {
        return new ExamRow(exam.getId(), exam.getSlug(), exam.getName(), String.join(", ", exam.getEligibleBatches()),
                format(exam.getOpenAt()), format(exam.getCloseAt()),
                format(exam.getResultReleaseAt()), counts.getOrDefault(exam.getId(), 0L), exam.answerKeyCount(),
                exam.getTotalQuestions(), status(exam), exam.isResultsPublished(), exam.getPaperDriveUrl(),
                exam.usesQuestionImages());
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
        if (exam.isResultsPublished()) return "RESULT RELEASED";
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
    private record BatchSnapshot(long expiresAtNanos, List<BatchRow> batches,
                                 List<String> activeBatchNames) {}
    private record ExamSnapshot(long expiresAtNanos, List<ExamRow> exams) {}
    private record DashboardSnapshot(long expiresAtNanos, Dashboard dashboard) {}
    public record BatchRow(Long id, String name, boolean active) {}
    public record PageResult<T>(List<T> items, int page, int totalPages, long totalItems,
                                boolean hasPrevious, boolean hasNext) {}
    public record AnalyticsBar(String label, int value, int height) {}
    public record QuestionPerformance(int question, int correct, int answered, int percentage, Double weight,
                                      String unit, Integer expectedSeconds, Integer averageSeconds,
                                      String timeVerdict) {
        public String expectedLabel() { return McqExamQuestion.formatTime(expectedSeconds); }
        public String averageLabel() { return McqExamQuestion.formatTime(averageSeconds); }
        public String weightText() { return McqExamQuestion.weightText(weight); }
        public String weightColor() { return McqExamQuestion.weightColor(weight); }
        public int fullStars() { return McqExamQuestion.fullStars(weight); }
        public boolean halfStar() { return McqExamQuestion.halfStar(weight); }
    }
    public record ExamAnalytics(ExamRow exam, String instructions, Integer durationMinutes,
                                List<AnalyticsBar> scoreDistribution, List<QuestionPerformance> questions,
                                List<SubmissionRow> submissions) {}
    /** One Question Editor save: the question as stored plus its syllabus tags (same transaction). */
    public record EditorSave(EditorQuestion question, List<McqExamQuestionService.TagView> tags) {}
    public record SaveResult(McqExam exam, boolean published, List<Integer> missingImages) {}
    public record EditorQuestion(int number, Long imageVersion, String correct, Double weight, Integer timeSeconds,
                                 List<String> units, List<String> competencyLevels, List<String> contents,
                                 List<String> learningOutcomes, boolean freeMark, List<Long> subImageIds) {
        public String subImagesText() { return subImageIds.stream().map(String::valueOf).collect(Collectors.joining(",")); }
        /** Newline-joined for the editor's data-* attributes (entries never contain line breaks). */
        public String unitsText() { return String.join("\n", units); }
        public String competencyLevelsText() { return String.join("\n", competencyLevels); }
        public String contentsText() { return String.join("\n", contents); }
        public String outcomesText() { return String.join("\n", learningOutcomes); }
    }
    public record QuestionEditor(ExamRow exam, boolean imageSheet, boolean published, List<EditorQuestion> questions) {}
    public record AnswerReview(int question, Integer selectedOption, List<Integer> correctOptions, String state,
                               Integer secondsSpent, Integer expectedSeconds, String timeVerdict) {
        public String spentLabel() { return McqExamQuestion.formatTime(secondsSpent); }
        public String spentShort() {
            return secondsSpent == null ? "-" : secondsSpent / 60 + ":" + String.format("%02d", secondsSpent % 60);
        }
        public String expectedLabel() { return McqExamQuestion.formatTime(expectedSeconds); }
    }
    public record SubmissionDetail(Long id, Long examId, String examName, String receipt, String studentName,
                                   String registrationId, String nic, String email, String school, String batch,
                                   String stream, String district, String started, String submitted, String status,
                                   Integer score, Double percentage, Integer totalQuestions,
                                   boolean resultsPublished, List<AnswerReview> answers, boolean imageSheet) {}
    public record ExamRow(Long id, String slug, String name, String batches, String opens, String closes,
                          String resultRelease, long submissions, int keyCount, int totalQuestions,
                          String status, boolean resultsPublished, String paperUrl,
                          boolean imageSheet) {}
    public record PublicExamRow(Long id, String slug, String name, String period, String month, Integer year,
                                String batches, String opens, String closes, Integer durationMinutes,
                                Integer totalQuestions, String status) {}
    private record PublicExamSnapshot(long expiresAtNanos, List<PublicExamRow> rows) {}
    public record SubmissionRow(Long id, String receipt, String examName, String studentName,
                                String registrationId, String nic, String email, String batch,
                                String school, String stream, String district, String submitted,
                                String status, String score) {}
}
