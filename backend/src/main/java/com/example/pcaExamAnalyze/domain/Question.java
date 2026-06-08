package com.example.pcaExamAnalyze.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A single structured question within a paper. Every structured question is
 * worth 20 marks by default and belongs to one teacher-defined {@link Section}.
 */
@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
public class Question {

    /** Marks every structured question carries unless the teacher overrides it. */
    public static final int DEFAULT_MAX_MARKS = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paper_structure_id", nullable = false)
    private PaperStructure paperStructure;

    /** e.g. "Q1" … "Q4". */
    @Column(nullable = false)
    private String questionNumber;

    /** The section (topic area) this question tests. Eager so views can read its name safely. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;

    @Column(nullable = false)
    private Integer maxMarks = DEFAULT_MAX_MARKS;

    @Column(length = 1000)
    private String description;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<QuestionReference> references = new ArrayList<>();

    public Question(PaperStructure paperStructure, String questionNumber, Section section,
                    Integer maxMarks, String description) {
        this.paperStructure = paperStructure;
        this.questionNumber = questionNumber;
        this.section = section;
        this.maxMarks = (maxMarks == null) ? DEFAULT_MAX_MARKS : maxMarks;
        this.description = description;
    }
}
