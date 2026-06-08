package com.example.pcaExamAnalyze.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One student marking sheet across ALL years' papers. The student fills in the
 * marks they scored per question (any subset — blanks are simply left out), then
 * generates a report. A student can have several attempts (Attempt 1, 2, 3 …).
 * Totals are computed over whatever scores were entered.
 */
@Entity
@Table(name = "attempts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "attempt_number"}))
@Getter
@Setter
@NoArgsConstructor
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    /** Auto-incremented per student: 1, 2, 3 … */
    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(nullable = false)
    private LocalDate attemptDate = LocalDate.now();

    @Column(nullable = false)
    private Integer totalObtained = 0;

    @Column(nullable = false)
    private Integer totalMax = 0;

    @Column(nullable = false)
    private Double percentage = 0.0;

    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<QuestionScore> scores = new ArrayList<>();

    public Attempt(User student, Integer attemptNumber) {
        this.student = student;
        this.attemptNumber = attemptNumber;
    }

    /** Recompute stored totals from the current (entered) scores. */
    public void recomputeTotals() {
        this.totalObtained = scores.stream().mapToInt(QuestionScore::getMarksObtained).sum();
        this.totalMax = scores.stream().mapToInt(s -> s.getQuestion().getMaxMarks()).sum();
        this.percentage = totalMax == 0 ? 0.0 : (totalObtained * 100.0) / totalMax;
    }

    public long roundedPercentage() {
        return Math.round(percentage);
    }
}
