package com.example.pcaExamAnalyze.analysis;

import com.example.pcaExamAnalyze.domain.Classification;

import java.util.List;

/**
 * How a student performed in one section (across the chosen attempt(s)), with the
 * teacher's study resources to act on it. Only questions the student actually
 * entered marks for are counted.
 */
public record SectionResult(
        String section,
        String color,
        int obtained,
        int max,
        double percentage,
        Classification classification,
        int questionCount,
        List<RefItem> resources) {

    public long roundedPercentage() {
        return Math.round(percentage);
    }

    public String cssClass() {
        return classification.getCssClass();
    }
}
