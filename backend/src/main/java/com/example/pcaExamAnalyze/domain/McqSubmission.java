package com.example.pcaExamAnalyze.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "mcq_submissions", uniqueConstraints = @UniqueConstraint(columnNames = "receipt_number"), indexes = {
        @Index(name = "idx_mcq_submission_student_exam", columnList = "exam_id,registration_id,nic"),
        @Index(name = "idx_mcq_submission_status_time", columnList = "status,submitted_at")
})
@Getter
@Setter
@NoArgsConstructor
public class McqSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_number", nullable = false, unique = true, length = 50)
    private String receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private McqExam exam;

    @Column(nullable = false, length = 160)
    private String studentName;

    @Column(nullable = false, length = 40)
    private String registrationId;

    @Column(nullable = false, length = 20)
    private String nic;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(nullable = false, length = 50)
    private String batch;

    @Column(nullable = false, length = 220)
    private String school;

    @Column(nullable = false, length = 50)
    private String stream;

    @Column(nullable = false, length = 50)
    private String district;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private McqSubmissionStatus status = McqSubmissionStatus.IN_PROGRESS;

    private Instant startedAt;
    private Instant submittedAt;
    private Integer score;
    private Double percentage;
    private Integer correctCount;
    private Integer incorrectCount;
    private Integer unansweredCount;
    private Instant resultCalculatedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "mcq_submission_answers", joinColumns = @JoinColumn(name = "submission_id"))
    @MapKeyColumn(name = "question_number")
    @Column(name = "selected_option", nullable = false)
    private Map<Integer, Integer> answers = new LinkedHashMap<>();

    /** Seconds the student spent viewing each question (image-sheet exams); teacher analysis only. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "mcq_submission_question_times", joinColumns = @JoinColumn(name = "submission_id"))
    @MapKeyColumn(name = "question_number")
    @Column(name = "seconds_spent", nullable = false)
    @org.hibernate.annotations.BatchSize(size = 50)
    private Map<Integer, Integer> questionTimes = new LinkedHashMap<>();

    @Column(length = 1000)
    private String adminNote;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private Long version;

    public String maskedNic() {
        if (nic == null || nic.length() < 4) return "****";
        return nic.substring(0, 4) + "******" + nic.substring(nic.length() - 2);
    }
}
