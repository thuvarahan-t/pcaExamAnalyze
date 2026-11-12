package com.example.pcaExamAnalyze.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "package_campaign")
@Getter @Setter
public class PackageCampaign {
    @Id private Long id = 1L;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false) private Instant endsAt;
    @Column(nullable = false, length = 16) private String whatsappNumber;
}
