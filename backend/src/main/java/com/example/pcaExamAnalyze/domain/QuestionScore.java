package com.example.pcaExamAnalyze.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Marks a student entered for one question in one attempt.
 * Invariant (enforced server-side): 0 <= marksObtained <= question.maxMarks.
 */
@Entity
@Table(name = "question_scores")
@Getter
@Setter
@NoArgsConstructor
public class QuestionScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private Attempt attempt;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false)
    private Integer marksObtained = 0;

    public QuestionScore(Attempt attempt, Question question, Integer marksObtained) {
        this.attempt = attempt;
        this.question = question;
        this.marksObtained = marksObtained;
    }

    public double getPercentage() {
        int max = question.getMaxMarks();
        return max == 0 ? 0.0 : (marksObtained * 100.0) / max;
    }
}
