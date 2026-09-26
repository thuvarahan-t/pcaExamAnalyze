package com.example.pcaExamAnalyze.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Optional immutable supporting image; IDs also act as browser cache versions. */
@Entity
@Table(name = "mcq_question_sub_images", indexes = @Index(name = "idx_sub_image_exam", columnList = "exam_id,question_number"))
@Getter @Setter
public class McqSubImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "exam_id", nullable = false) private Long examId;
    @Column(name = "question_number", nullable = false) private Integer questionNumber;
    private String contentType;
    private String storageKey;
    @JdbcTypeCode(SqlTypes.VARBINARY) @Column(columnDefinition = "bytea") private byte[] data;
}
