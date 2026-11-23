package com.example.pcaExamAnalyze.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class OfferEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 140)
    private String title;
    private LocalDate eventDate;
    @Column(nullable = false, length = 140)
    private String venue;
    @Column(nullable = false, length = 480)
    private String description;
    @Column(nullable = false)
    private boolean published;
}
