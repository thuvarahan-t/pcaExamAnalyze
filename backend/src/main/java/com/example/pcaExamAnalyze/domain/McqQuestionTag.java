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
 * Links a question to one syllabus unit + competency level, with the ticked content items and
 * learning outcomes. A question can have several tags (it may span 2-3 units). Unit/level ids
 * point into the syllabus, and the names are snapshotted so tags stay readable if the syllabus
 * is later edited or a unit is deleted.
 */
@Entity
@Table(name = "mcq_question_syllabus_tags")
@Getter
@Setter
@NoArgsConstructor
public class McqQuestionTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private McqExamQuestion question;

    @Column(nullable = false)
    private Integer position;

    private Long unitId;

    private Long levelId;

    @Column(length = 200)
    private String unitName;

    @Column(length = 1000)
    private String competency;

    @Column(length = 300)
    private String levelName;

    /** Ticked content items as tree paths ("Kinematics › Displacement"), newline-separated. */
    @Column(name = "content_paths", length = 8000)
    private String contentPaths;

    /** Ticked learning outcomes, newline-separated. */
    @Column(length = 8000)
    private String outcomes;
}
