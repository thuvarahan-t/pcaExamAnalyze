package com.example.pcaExamAnalyze.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One syllabus unit. A unit has exactly one competency and any number of competency levels. */
@Entity
@Table(name = "syllabus_units")
@Getter
@Setter
@NoArgsConstructor
public class SyllabusUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String competency;

    /** Periods for the whole unit; the teacher divides them across the competency levels. */
    private Integer totalPeriods;

    @OneToMany(mappedBy = "unit", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<SyllabusLevel> levels = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}
