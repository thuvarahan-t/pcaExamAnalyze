package com.example.pcaExamAnalyze.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Kept separate so catalog/count requests never load image bytes. */
@Entity
@Table(name = "package_posters")
@Getter @Setter
public class PackagePoster {
    @Id private Long id;
    @Column(nullable = false, length = 30) private String contentType;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARBINARY)
    @Column(nullable = false, columnDefinition = "bytea") private byte[] data;
}
