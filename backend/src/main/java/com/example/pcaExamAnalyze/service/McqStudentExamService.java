package com.example.pcaExamAnalyze.service;

import com.example.pcaExamAnalyze.domain.McqExam;
import com.example.pcaExamAnalyze.domain.McqExamPublicationState;
import com.example.pcaExamAnalyze.domain.McqExamQuestion;
import com.example.pcaExamAnalyze.repo.McqQuestionMeta;
import com.example.pcaExamAnalyze.domain.McqSubmission;
import com.example.pcaExamAnalyze.domain.McqSubmissionStatus;
import com.example.pcaExamAnalyze.repo.McqExamRepository;
import com.example.pcaExamAnalyze.repo.McqSubmissionRepository;
import com.example.pcaExamAnalyze.web.dto.ExamStudentDetailsForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class McqStudentExamService {

    private static final Pattern DRIVE_FILE = Pattern.compile("(?:/d/|[?&]id=)([A-Za-z0-9_-]{10,})");

    private final McqExamRepository exams;
    private final McqSubmissionRepository submissions;
    private final McqExamQuestionService questionImages;

    public McqStudentExamService(McqExamRepository exams, McqSubmissionRepository submissions,
                                 McqExamQuestionService questionImages) {
        this.exams = exams;
        this.submissions = submissions;
        this.questionImages = questionImages;
    }

    @Transactional(readOnly = true)
    public ExamDetails examDetails(String slug) {
        McqExam exam = requirePublishedExam(slug);
        String status = status(exam);
        return new ExamDetails(exam.getId(), exam.getSlug(), exam.getName(), exam.getInstructions(),
                exam.getExamMonth() + " " + exam.getExamYear(), String.join(", ", exam.getEligibleBatches()),
                McqAdminService.STREAMS, exam.getTotalQuestions(), exam.getOptionsPerQuestion(),
                McqAdminService.formatPublic(exam.getOpenAt()),
                exam.getDurationMinutes(), status, isAvailable(exam), exam.isResultsPublished(),
                exam.isAllowResubmission(), exam.getPaperDriveUrl());
    }

    @Transactional(readOnly = true)
    public Set<Long> submittedExamIds(ExamStudentDetailsForm details) {
        if (details == null || blank(details.getRegistrationId()) || blank(details.getNic())) return Set.of();
        return submissions.findSubmittedExamIds(details.getRegistrationId(), details.getNic());
    }

    @Transactional(readOnly = true)
    public boolean alreadySubmitted(Long examId, ExamStudentDetailsForm details) {
        return details != null && !blank(details.getRegistrationId()) && !blank(details.getNic())
                && submissions.existsByExamIdAndRegistrationIdIgnoreCaseAndNicAndStatus(
                examId, details.getRegistrationId(), details.getNic(), McqSubmissionStatus.SUBMITTED);
    }

    @Transactional
    public McqSubmission startOrResume(String slug, ExamStudentDetailsForm details) {
        if (details == null) throw new IllegalStateException("Enter and save your details before starting the paper");
        McqExam exam = requirePublishedExam(slug);
        if (!isAvailable(exam)) throw new IllegalStateException("This examination is not open for answers yet");
        McqSubmission submission = submissions
                .findFirstByExamIdAndRegistrationIdIgnoreCaseAndNicOrderByCreatedAtDesc(
                        exam.getId(), details.getRegistrationId(), details.getNic())
                .orElseGet(McqSubmission::new);
        if (submission.getId() != null && submission.getStatus() == McqSubmissionStatus.SUBMITTED) return submission;
        if (submission.getId() != null && overdue(submission)) {
            // The paper's time ran out while the student was away: it is submitted as it stands.
            finalizeSubmission(submission);
            return submission;
        }
        if (submission.getId() == null) {
            submission.setExam(exam);
            submission.setReceiptNumber("PCA-" + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 12).toUpperCase(Locale.ROOT));
            submission.setStartedAt(Instant.now());
            submission.setStatus(McqSubmissionStatus.IN_PROGRESS);
        }
        copyStudent(details, submission);
        submission.setUpdatedAt(Instant.now());
        return submissions.save(submission);
    }

    @Transactional(readOnly = true)
    public Workspace workspace(Long submissionId, ExamStudentDetailsForm details) {
        McqSubmission submission = requireOwned(submissionId, details);
        McqExam exam = submission.getExam();
        long remaining = remainingSeconds(submission);
        return new Workspace(submission.getId(), exam.getSlug(), exam.getName(), exam.getInstructions(),
                exam.getTotalQuestions(), exam.getOptionsPerQuestion(), exam.getDurationMinutes(), remaining,
                previewUrl(exam.getPaperDriveUrl()), exam.getPaperDriveUrl(), submission.getReceiptNumber(),
                submission.getStatus().name(), new LinkedHashMap<>(submission.getAnswers()),
                exam.effectiveSheetType().name(), stepQuestions(exam));
    }

    private Map<Integer, StepQuestion> stepQuestions(McqExam exam) {
        if (!exam.usesQuestionImages()) return Map.of();
        Map<Integer, StepQuestion> result = new LinkedHashMap<>();
        Map<Integer, McqQuestionMeta> meta = questionImages.meta(exam.getId());
        Map<Integer, List<Long>> supporting = questionImages.subImageIds(exam.getId());
        for (int q = 1; q <= exam.getTotalQuestions(); q++) {
            McqQuestionMeta m = meta.get(q);
            result.put(q, new StepQuestion(q, m == null ? null : m.imageVersion(), m == null ? null : m.weight(), supporting.getOrDefault(q, List.of())));
        }
        return result;
    }

    /** A question image, readable only by the student who owns this submission. */
    @Transactional(readOnly = true)
    public Optional<McqExamQuestion> questionImage(Long submissionId, int question, ExamStudentDetailsForm details) {
        McqExam exam = requireOwned(submissionId, details).getExam();
        if (!exam.usesQuestionImages() || question < 1 || question > exam.getTotalQuestions()) return Optional.empty();
        return questionImages.findImage(exam.getId(), question);
    }

    @Transactional
    public SaveStatus saveAnswer(Long submissionId, int question, Integer option, ExamStudentDetailsForm details) {
        McqSubmission submission = requireOwned(submissionId, details);
        if (submission.getStatus() != McqSubmissionStatus.IN_PROGRESS) {
            throw new IllegalStateException("This paper has already been submitted");
        }
        McqExam exam = submission.getExam();
        if (!isAvailable(exam) || !acceptsAnswers(submission)) {
            throw new IllegalStateException("The answering time has ended");
        }
        if (question < 1 || question > exam.getTotalQuestions()) throw new IllegalArgumentException("Invalid question");
        if (option == null) submission.getAnswers().remove(question);
        else {
            if (option < 1 || option > exam.getOptionsPerQuestion()) throw new IllegalArgumentException("Invalid option");
            submission.getAnswers().put(question, option);
        }
        submission.setUpdatedAt(Instant.now());
        submissions.save(submission);
        return new SaveStatus(submission.getAnswers().size(), exam.getTotalQuestions(), remainingSeconds(submission));
    }

    @Transactional
    public SaveStatus saveAnswers(Long submissionId, Map<Integer, Integer> answers,
                                  ExamStudentDetailsForm details) {
        return saveAnswers(submissionId, answers, Map.of(), details);
    }

    /** questionTimes are cumulative seconds per question; the stored value only ever grows. */
    @Transactional
    public SaveStatus saveAnswers(Long submissionId, Map<Integer, Integer> answers,
                                  Map<Integer, Integer> questionTimes, ExamStudentDetailsForm details) {
        McqSubmission submission = requireOwned(submissionId, details);
        if (submission.getStatus() != McqSubmissionStatus.IN_PROGRESS) {
            throw new IllegalStateException("This paper has already been submitted");
        }
        McqExam exam = submission.getExam();
        if (!isAvailable(exam) || !acceptsAnswers(submission)) {
            throw new IllegalStateException("The answering time has ended");
        }
        Map<Integer, Integer> validated = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> answer : answers.entrySet()) {
            int question = answer.getKey();
            Integer option = answer.getValue();
            if (question < 1 || question > exam.getTotalQuestions()) {
                throw new IllegalArgumentException("Invalid question");
            }
            if (option == null || option < 1 || option > exam.getOptionsPerQuestion()) {
                throw new IllegalArgumentException("Invalid option");
            }
            validated.put(question, option);
        }
        // Change only the rows that differ: clear() + putAll() rewrote every answer row on each autosave.
        submission.getAnswers().keySet().retainAll(validated.keySet());
        validated.forEach((question, option) -> {
            if (!option.equals(submission.getAnswers().get(question))) submission.getAnswers().put(question, option);
        });
        questionTimes.forEach((question, seconds) -> {
            if (question == null || seconds == null || question < 1 || question > exam.getTotalQuestions()) return;
            int bounded = Math.max(0, Math.min(seconds, 6 * 60 * 60));
            submission.getQuestionTimes().merge(question, bounded, Math::max);
        });
        submission.setUpdatedAt(Instant.now());
        submissions.save(submission);
        return new SaveStatus(validated.size(), exam.getTotalQuestions(), remainingSeconds(submission));
    }

    @Transactional
    public Result submit(Long submissionId, ExamStudentDetailsForm details) {
        McqSubmission submission = requireOwned(submissionId, details);
        if (submission.getStatus() == McqSubmissionStatus.SUBMITTED) return resultOf(submission);
        McqExam exam = submission.getExam();
        if (exam.getPublicationState() != McqExamPublicationState.PUBLISHED) {
            throw new IllegalStateException("This examination is no longer available");
        }
        finalizeSubmission(submission);
        return resultOf(submission);
    }

    /** Submits the paper if its time (plus the grace period) is over. Returns true when it did. */
    @Transactional
    public boolean autoSubmitIfOverdue(Long submissionId, ExamStudentDetailsForm details) {
        McqSubmission submission = requireOwned(submissionId, details);
        if (submission.getStatus() != McqSubmissionStatus.IN_PROGRESS || !overdue(submission)) return false;
        finalizeSubmission(submission);
        return true;
    }

    /**
     * Background sweep: submits every in-progress paper whose time ran out, so papers are
     * finalised even when the student closed the browser before the timer reached zero.
     */
    @Transactional
    public int autoSubmitOverdue() {
        int count = 0;
        for (McqSubmission submission : submissions.findTimedInProgress()) {
            if (overdue(submission)) {
                finalizeSubmission(submission);
                count++;
            }
        }
        return count;
    }

    private void finalizeSubmission(McqSubmission submission) {
        McqScoring.score(submission.getExam(), submission);
        submission.setStatus(McqSubmissionStatus.SUBMITTED);
        submission.setSubmittedAt(Instant.now());
        submission.setUpdatedAt(Instant.now());
        submissions.save(submission);
    }

    @Transactional(readOnly = true)
    public Result result(String slug, ExamStudentDetailsForm details) {
        if (details == null) throw new IllegalStateException("Enter your details to view this result");
        return result(slug, details.getRegistrationId(), details.getNic());
    }

    @Transactional(readOnly = true)
    public Result result(String slug, String registrationId, String nic) {
        String normalizedRegistration = registrationId == null ? "" : registrationId.trim().toUpperCase(Locale.ROOT);
        String normalizedNic = nic == null ? "" : nic.trim();
        if (normalizedRegistration.isBlank() || !normalizedNic.matches("[0-9]{12}")) {
            throw new IllegalStateException("We could not find a matching result. Check the Registration ID and NIC number");
        }
        McqExam exam = requirePublishedExam(slug);
        McqSubmission submission = submissions
                .findFirstByExamIdAndRegistrationIdIgnoreCaseAndNicOrderByCreatedAtDesc(
                        exam.getId(), normalizedRegistration, normalizedNic)
                .orElseThrow(() -> new IllegalStateException(
                        "We could not find a matching result. Check the Registration ID and NIC number"));
        if (submission.getStatus() != McqSubmissionStatus.SUBMITTED) {
            throw new IllegalStateException("This examination has not been submitted yet");
        }
        return resultOf(submission);
    }

    @Transactional(readOnly = true)
    public ResultDashboard resultsDashboard(String registrationId, String nic) {
        LookupIdentity identity = normalizeLookup(registrationId, nic);
        List<McqSubmission> source = submissions
                .findByRegistrationIdIgnoreCaseAndNicAndStatusOrderBySubmittedAtDesc(
                        identity.registrationId(), identity.nic(), McqSubmissionStatus.SUBMITTED);
        List<ResultCard> cards = source.stream().map(this::resultCard).toList();
        List<ResultCard> released = cards.stream()
                .filter(ResultCard::released).filter(card -> card.percentage() != null).toList();
        double average = released.stream().map(ResultCard::percentage)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0);
        List<ResultCard> latestFive = released.stream().limit(5).toList();
        double latestFiveAverage = latestFive.stream().map(ResultCard::percentage)
                .mapToDouble(Double::doubleValue).average().orElse(0);
        List<ResultPoint> graph = new ArrayList<>();
        for (int index = latestFive.size() - 1; index >= 0; index--) {
            ResultCard card = latestFive.get(index);
            graph.add(new ResultPoint(card.shortName(), card.percentage(),
                    Math.max(4, (int) Math.round(card.percentage()))));
        }
        String studentName = source.isEmpty() ? "Student" : source.getFirst().getStudentName();
        return new ResultDashboard(studentName, identity.registrationId(), identity.nic(), cards.size(),
                released.size(), average, latestFiveAverage, cards, graph,
                cards.stream().map(ResultCard::batch).filter(value -> !blank(value)).distinct().sorted().toList(),
                cards.stream().map(ResultCard::month).distinct().toList(),
                cards.stream().map(ResultCard::year).distinct().sorted(java.util.Comparator.reverseOrder()).toList());
    }

    @Transactional(readOnly = true)
    public Result resultBySubmission(Long submissionId, String registrationId, String nic) {
        LookupIdentity identity = normalizeLookup(registrationId, nic);
        McqSubmission submission = submissions.findStudentSessionById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Result not found"));
        if (!submission.getRegistrationId().equalsIgnoreCase(identity.registrationId())
                || !submission.getNic().equals(identity.nic())
                || submission.getStatus() != McqSubmissionStatus.SUBMITTED) {
            throw new IllegalStateException("This result does not match the entered Registration ID and NIC number");
        }
        return resultOf(submission);
    }

    private ResultCard resultCard(McqSubmission submission) {
        McqExam exam = submission.getExam();
        boolean released = resultReleased(exam);
        String shortName = exam.getName().length() > 16 ? exam.getName().substring(0, 16) + "…" : exam.getName();
        return new ResultCard(submission.getId(), exam.getSlug(), exam.getName(), shortName,
                exam.getExamMonth() + " " + exam.getExamYear(), exam.getExamMonth(), exam.getExamYear(),
                submission.getBatch(), McqAdminService.formatPublic(submission.getSubmittedAt()),
                exam.getTotalQuestions(), released,
                released ? submission.getScore() : null, released ? submission.getPercentage() : null,
                exam.getPaperDriveUrl(), previewUrl(exam.getPaperDriveUrl()), thumbnailUrl(exam.getPaperDriveUrl()));
    }

    private Result resultOf(McqSubmission submission) {
        McqExam exam = submission.getExam();
        boolean released = resultReleased(exam);
        List<ResultAnswer> answers = new ArrayList<>();
        for (int q = 1; q <= exam.getTotalQuestions(); q++) {
            Integer selected = submission.getAnswers().get(q);
            List<Integer> correct = released ? new ArrayList<>(exam.correctOptions(q)) : List.of();
            String state = !released ? (selected == null ? "UNANSWERED" : "ANSWERED")
                    : exam.isFreeMark(q) ? "FREE_MARK" : selected == null ? "UNANSWERED" : correct.contains(selected) ? "CORRECT" : "INCORRECT";
            answers.add(new ResultAnswer(q, selected, correct, state));
        }
        return new Result(submission.getId(), exam.getSlug(), exam.getName(), submission.getReceiptNumber(),
                submission.getStudentName(), submission.getRegistrationId(), submission.getNic(), submission.getEmail(),
                submission.getBatch(), submission.getSchool(), submission.getStream(), submission.getDistrict(),
                McqAdminService.formatPublic(submission.getStartedAt()), McqAdminService.formatPublic(submission.getSubmittedAt()),
                exam.getTotalQuestions(), submission.getAnswers().size(), released, released ? submission.getScore() : null,
                released ? submission.getPercentage() : null, exam.getPaperDriveUrl(), previewUrl(exam.getPaperDriveUrl()),
                thumbnailUrl(exam.getPaperDriveUrl()), answers, exam.usesQuestionImages(), imageVersions(exam),
                exam.usesQuestionImages() ? questionImages.subImageIds(exam.getId()) : Map.of());
    }

    private Map<Integer, Long> imageVersions(McqExam exam) {
        if (!exam.usesQuestionImages()) return Map.of();
        Map<Integer, Long> versions = new LinkedHashMap<>();
        questionImages.meta(exam.getId()).forEach((q, meta) -> { if (meta.imageVersion() != null) versions.put(q, meta.imageVersion()); });
        return versions;
    }

    /**
     * A question image for the result review, readable by the student who submitted the paper
     * (identified by the saved details or result-lookup Registration ID + NIC).
     */
    @Transactional(readOnly = true)
    public Optional<McqExamQuestion> resultQuestionImage(Long submissionId, int question, String registrationId, String nic) {
        if (registrationId == null || nic == null) return Optional.empty();
        McqSubmission submission = submissions.findStudentSessionById(submissionId).orElse(null);
        if (submission == null || submission.getStatus() != McqSubmissionStatus.SUBMITTED
                || !submission.getRegistrationId().equalsIgnoreCase(registrationId.trim())
                || !submission.getNic().equals(nic.replaceAll("\\D", ""))) {
            return Optional.empty();
        }
        McqExam exam = submission.getExam();
        if (!exam.usesQuestionImages() || question < 1 || question > exam.getTotalQuestions()) return Optional.empty();
        return questionImages.findImage(exam.getId(), question);
    }

    private McqSubmission requireOwned(Long id, ExamStudentDetailsForm details) {
        if (details == null) throw new IllegalStateException("Your saved student details are required");
        McqSubmission submission = submissions.findStudentSessionById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exam session not found"));
        if (!submission.getRegistrationId().equalsIgnoreCase(details.getRegistrationId())
                || !submission.getNic().equals(details.getNic())) {
            throw new IllegalStateException("This exam session does not belong to the saved student details");
        }
        return submission;
    }

    private McqExam requirePublishedExam(String slug) {
        McqExam exam = exams.findStudentExamBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found"));
        if (exam.getPublicationState() != McqExamPublicationState.PUBLISHED) {
            throw new IllegalArgumentException("Exam not found");
        }
        return exam;
    }

    private static void copyStudent(ExamStudentDetailsForm details, McqSubmission submission) {
        submission.setStudentName(details.getFullName());
        submission.setRegistrationId(details.getRegistrationId());
        submission.setNic(details.getNic());
        submission.setEmail(details.getEmail());
        submission.setBatch(details.getBatch());
        submission.setSchool(details.getSchool());
        submission.setStream(details.getStream());
        submission.setDistrict(details.getDistrict());
    }

    private static String status(McqExam exam) {
        Instant now = Instant.now();
        if (exam.isResultsPublished() && !now.isBefore(exam.getResultReleaseAt())) return "RESULT RELEASED";
        if (now.isBefore(exam.getOpenAt())) return "SCHEDULED";
        return "OPEN";
    }

    private static boolean isAvailable(McqExam exam) {
        return !Instant.now().isBefore(exam.getOpenAt());
    }

    /** Answers sent this many seconds after the deadline are still accepted (network delay at 00:00). */
    private static final long ANSWER_GRACE_SECONDS = 20;

    private static Instant deadline(McqSubmission submission) {
        Integer minutes = submission.getExam().getDurationMinutes();
        if (minutes == null || minutes <= 0 || submission.getStartedAt() == null) return null;
        return submission.getStartedAt().plusSeconds(minutes * 60L);
    }

    private static boolean acceptsAnswers(McqSubmission submission) {
        Instant deadline = deadline(submission);
        return deadline == null || Instant.now().isBefore(deadline.plusSeconds(ANSWER_GRACE_SECONDS));
    }

    private static boolean overdue(McqSubmission submission) {
        return submission.getStatus() == McqSubmissionStatus.IN_PROGRESS && !acceptsAnswers(submission);
    }

    private static long remainingSeconds(McqSubmission submission) {
        Integer minutes = submission.getExam().getDurationMinutes();
        if (minutes == null || minutes <= 0 || submission.getStartedAt() == null) return -1;
        return Math.max(0, Duration.between(Instant.now(), submission.getStartedAt().plusSeconds(minutes * 60L)).getSeconds());
    }

    private static String previewUrl(String url) {
        if (url == null) return "";
        Matcher matcher = DRIVE_FILE.matcher(url);
        return matcher.find() ? "https://drive.google.com/file/d/" + matcher.group(1) + "/preview" : url;
    }

    private static String thumbnailUrl(String url) {
        if (url == null) return "";
        Matcher matcher = DRIVE_FILE.matcher(url);
        return matcher.find() ? "https://drive.google.com/thumbnail?id=" + matcher.group(1) + "&sz=w600" : "";
    }

    private static boolean resultReleased(McqExam exam) {
        return exam.isResultsPublished() && !Instant.now().isBefore(exam.getResultReleaseAt());
    }

    private static LookupIdentity normalizeLookup(String registrationId, String nic) {
        String normalizedRegistration = registrationId == null ? "" : registrationId.trim().toUpperCase(Locale.ROOT);
        String normalizedNic = nic == null ? "" : nic.replaceAll("\\D", "");
        if (normalizedRegistration.isBlank() || normalizedRegistration.length() > 40 || !normalizedNic.matches("[0-9]{12}")) {
            throw new IllegalArgumentException("Enter a valid Registration ID and 12 digit NIC number");
        }
        return new LookupIdentity(normalizedRegistration, normalizedNic);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record ExamDetails(Long id, String slug, String name, String instructions, String period, String batches,
                              List<String> streams, Integer totalQuestions, Integer optionsPerQuestion,
                              String opens, Integer durationMinutes, String status, boolean canStart,
                              boolean resultsPublished, boolean allowResubmission, String paperUrl) {}
    public record Workspace(Long submissionId, String slug, String examName, String instructions, Integer totalQuestions,
                            Integer optionsPerQuestion, Integer durationMinutes, long remainingSeconds,
                            String previewUrl, String paperUrl, String receipt, String status,
                            LinkedHashMap<Integer, Integer> answers, String sheetType,
                            Map<Integer, StepQuestion> questions) {
        public boolean questionImages() { return "QUESTION_IMAGES".equals(sheetType); }
    }
    /** What the student sees per question: image version (null = no image) and weight 1-5 in 0.5 steps. */
    public record StepQuestion(int number, Long imageVersion, Double weight, List<Long> subImageIds) {}
    public record SaveStatus(int answered, int total, long remainingSeconds) {}
    public record ResultAnswer(int question, Integer selected, List<Integer> correct, String state) {}
    public record Result(Long submissionId, String slug, String examName, String receipt, String studentName,
                         String registrationId, String nic, String email, String batch, String school, String stream,
                         String district, String startedAt, String submittedAt, Integer totalQuestions, Integer answered,
                         boolean released, Integer score, Double percentage, String paperUrl, String previewUrl,
                         String thumbnailUrl, List<ResultAnswer> answers, boolean questionImages,
                         Map<Integer, Long> imageVersions, Map<Integer, List<Long>> subImageIds) {}
    public record ResultCard(Long submissionId, String slug, String examName, String shortName, String period,
                             String month, Integer year, String batch, String submittedAt,
                             Integer totalQuestions, boolean released, Integer score, Double percentage,
                             String paperUrl, String previewUrl, String thumbnailUrl) {}
    public record ResultPoint(String label, Double percentage, int height) {}
    public record ResultDashboard(String studentName, String registrationId, String nic, int totalExams,
                                  int releasedResults, double averagePercentage, double latestFiveAverage,
                                  List<ResultCard> results, List<ResultPoint> graph, List<String> batches,
                                  List<String> months, List<Integer> years) {}
    private record LookupIdentity(String registrationId, String nic) {}
}
