package com.example.pcaExamAnalyze.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * One Physics past paper for a given year (2021–2025), configured by the teacher.
 */
@Entity
@Table(name = "paper_structures")
@Getter
@Setter
@NoArgsConstructor
public class PaperStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paper_year", nullable = false, unique = true)
    private Integer year;

    @Column(nullable = false)
    private String subject = "Physics";

    @Column(nullable = false)
    private String title;

    @OneToMany(mappedBy = "paperStructure", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Question> questions = new ArrayList<>();

    public PaperStructure(Integer year, String subject, String title) {
        this.year = year;
        this.subject = subject;
        this.title = title;
    }

    public int getMaxTotal() {
        return questions.stream().mapToInt(Question::getMaxMarks).sum();
    }
}
