package com.example.pcaExamAnalyze.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A competency level of a {@link SyllabusUnit}: its periods, a content tree and learning outcomes.
 * The content tree is stored as lines indented with one tab per depth (entries are single-line),
 * and learning outcomes as newline-separated lines; SyllabusService converts both.
 */
@Entity
@Table(name = "syllabus_levels")
@Getter
@Setter
@NoArgsConstructor
public class SyllabusLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private SyllabusUnit unit;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false, length = 300)
    private String name;

    private Integer periods;

    @Column(name = "content_tree", length = 20000)
    private String contentTree;

    @Column(length = 8000)
    private String outcomes;
}
