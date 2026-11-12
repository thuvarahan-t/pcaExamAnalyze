package com.example.pcaExamAnalyze.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "class_packages")
@Getter @Setter
public class ClassPackage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 160) private String name;
    @Column(nullable = false, length = 40) private String category;
    @Column(nullable = false, length = 100) private String batch;
    @Column(nullable = false, length = 2000) private String description;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal originalPrice;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal offerPrice;
    @Column(nullable = false) private int capacity = 111;
    @Column(nullable = false) private int displayOrder;
    @Column(nullable = false) private boolean published;
    @Column(nullable = false) private long posterVersion = 1;
}
