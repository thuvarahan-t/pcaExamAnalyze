package com.example.pcaExamAnalyze.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Study material the teacher attaches to a question — the "what to study if weak" data.
 */
@Entity
@Table(name = "question_references")
@Getter
@Setter
@NoArgsConstructor
public class QuestionReference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReferenceType type;

    /** e.g. "Physics Resource Book — pg 112–118" or a URL. */
    @Column(nullable = false, length = 1000)
    private String resource;

    @Column(length = 1000)
    private String note;

    public QuestionReference(Question question, String title, ReferenceType type, String resource, String note) {
        this.question = question;
        this.title = title;
        this.type = type;
        this.resource = resource;
        this.note = note;
    }

    /** True when the resource looks like a clickable URL. */
    public boolean isUrl() {
        return resource != null && (resource.startsWith("http://") || resource.startsWith("https://"));
    }
}
