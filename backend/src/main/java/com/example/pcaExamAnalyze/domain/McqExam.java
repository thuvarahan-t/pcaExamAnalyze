package com.example.pcaExamAnalyze.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "mcq_exams", uniqueConstraints = @UniqueConstraint(columnNames = "slug"), indexes = {
        @Index(name = "idx_mcq_exam_student_list", columnList = "publication_state,created_at"),
        @Index(name = "idx_mcq_exam_period", columnList = "exam_year,exam_month")
})
@Getter
@Setter
@NoArgsConstructor
public class McqExam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(nullable = false, unique = true, length = 200)
    private String slug;

    @Column(length = 3000)
    private String instructions;

    @Column(nullable = false)
    private Integer examYear;

    @Column(nullable = false, length = 20)
    private String examMonth;

    @Column(nullable = false, length = 1000)
    private String paperDriveUrl;

    @Column(nullable = false)
    private Integer totalQuestions = 50;

    @Column(nullable = false)
    private Integer optionsPerQuestion = 5;

    @Column(nullable = false)
    private Instant openAt;

    @Column(nullable = false)
    private Instant closeAt;

    @Column(nullable = false)
    private Instant resultReleaseAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private McqExamPublicationState publicationState = McqExamPublicationState.DRAFT;

    @Column(nullable = false)
    private boolean manualClose;

    @Column(nullable = false)
    private boolean resultsPublished;

    @Column(nullable = false)
    private boolean allowResubmission;

    private Integer durationMinutes;

    private Integer passMark;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "mcq_exam_batches", joinColumns = @JoinColumn(name = "exam_id"))
    @Column(name = "batch_name", nullable = false, length = 50)
    private Set<String> eligibleBatches = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "mcq_exam_streams", joinColumns = @JoinColumn(name = "exam_id"))
    @Column(name = "stream_name", nullable = false, length = 50)
    private Set<String> eligibleStreams = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "mcq_answer_keys", joinColumns = @JoinColumn(name = "exam_id"))
    @MapKeyColumn(name = "question_number")
    @Column(name = "correct_option", nullable = false)
    private Map<Integer, Integer> answerKey = new LinkedHashMap<>();

    /**
     * Comma-separated accepted options per question. Kept in a separate table so
     * existing single-answer data in mcq_answer_keys remains backward compatible.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "mcq_accepted_answer_keys", joinColumns = @JoinColumn(name = "exam_id"))
    @MapKeyColumn(name = "question_number")
    @Column(name = "accepted_options", nullable = false, length = 20)
    private Map<Integer, String> acceptedAnswerKeys = new LinkedHashMap<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    private Instant archivedAt;

    @Version
    private Long version;

    public int answerKeyCount() {
        return (int) java.util.stream.IntStream.rangeClosed(1, totalQuestions)
                .filter(question -> !correctOptions(question).isEmpty())
                .count();
    }

    public boolean hasCompleteAnswerKey() {
        return answerKeyCount() == totalQuestions;
    }

    public Set<Integer> correctOptions(int question) {
        LinkedHashSet<Integer> options = new LinkedHashSet<>();
        String encoded = acceptedAnswerKeys.get(question);
        if (encoded != null && !encoded.isBlank()) {
            for (String item : encoded.split(",")) {
                try {
                    int option = Integer.parseInt(item.trim());
                    if (option >= 1 && option <= optionsPerQuestion) options.add(option);
                } catch (NumberFormatException ignored) {
                    // Ignore malformed legacy values instead of breaking an exam.
                }
            }
        }
        if (options.isEmpty()) {
            Integer legacy = answerKey.get(question);
            if (legacy != null && legacy >= 1 && legacy <= optionsPerQuestion) options.add(legacy);
        }
        return options;
    }

    public Map<Integer, List<Integer>> correctAnswerOptions() {
        Map<Integer, List<Integer>> result = new LinkedHashMap<>();
        for (int question = 1; question <= totalQuestions; question++) {
            Set<Integer> options = correctOptions(question);
            if (!options.isEmpty()) result.put(question, new ArrayList<>(options));
        }
        return result;
    }

    public void setCorrectAnswerOptions(Map<Integer, List<Integer>> answers) {
        Map<Integer, String> encoded = new LinkedHashMap<>();
        Map<Integer, Integer> legacyPrimary = new LinkedHashMap<>();
        if (answers != null) {
            answers.forEach((question, options) -> {
                if (question == null || options == null || question < 1 || question > totalQuestions) return;
                List<Integer> valid = options.stream().filter(java.util.Objects::nonNull)
                        .filter(option -> option >= 1 && option <= optionsPerQuestion).distinct().sorted().toList();
                if (!valid.isEmpty()) {
                    encoded.put(question, valid.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
                    legacyPrimary.put(question, valid.getFirst());
                }
            });
        }
        this.acceptedAnswerKeys = encoded;
        this.answerKey = legacyPrimary;
    }
}
