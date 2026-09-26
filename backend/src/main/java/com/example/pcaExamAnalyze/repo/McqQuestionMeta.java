package com.example.pcaExamAnalyze.repo;

import java.time.Instant;

/** Question metadata without the image bytes; imageUpdatedAt is null when there is no image. */
public record McqQuestionMeta(Integer questionNumber, Instant imageUpdatedAt, Double weight, Integer timeSeconds,
                              String units, String competencyLevels, String contents,
                              String learningOutcomes, String storageKey) {
    public boolean hasImage() { return imageUpdatedAt != null; }
    public java.util.List<String> unitList() { return com.example.pcaExamAnalyze.domain.McqExamQuestion.splitList(units); }
    public java.util.List<String> competencyLevelList() { return com.example.pcaExamAnalyze.domain.McqExamQuestion.splitList(competencyLevels); }
    public java.util.List<String> contentList() { return com.example.pcaExamAnalyze.domain.McqExamQuestion.splitList(contents); }
    public java.util.List<String> outcomeList() { return com.example.pcaExamAnalyze.domain.McqExamQuestion.splitList(learningOutcomes); }
    public Long imageVersion() { return imageUpdatedAt == null ? null : imageUpdatedAt.toEpochMilli(); }
}
