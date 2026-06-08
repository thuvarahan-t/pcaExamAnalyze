package com.example.pcaExamAnalyze.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A teacher-managed category that a structured question belongs to —
 * e.g. "Mechanics", "Waves", "Electricity". Shared across every year's paper,
 * and the unit the student analysis groups marks by.
 */
@Entity
@Table(name = "sections")
@Getter
@Setter
@NoArgsConstructor
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    public Section(String name) {
        this.name = name;
    }
}
