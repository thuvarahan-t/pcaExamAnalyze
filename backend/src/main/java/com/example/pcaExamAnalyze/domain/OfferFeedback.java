package com.example.pcaExamAnalyze.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class OfferFeedback {
    public enum Status { PENDING, PUBLISHED, HIDDEN }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 150)
    private String studentName;
    @Column(nullable = false, length = 150)
    private String school;
    @Column(nullable = false, length = 40)
    private String district;
    @Column(nullable = false, length = 160)
    private String packageName;
    @Column(nullable = false, length = 600)
    private String comment;
    @Column(nullable = false)
    private Instant receivedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;
}
