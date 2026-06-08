package com.example.pcaExamAnalyze.analysis;

import com.example.pcaExamAnalyze.domain.Classification;

import java.util.List;

/**
 * A student's generated report over one attempt or all attempts combined.
 * Sections are bucketed into strong / mid / weak using the teacher's thresholds.
 */
public record ReportData(
        String scopeLabel,      // "Attempt 3" or "All attempts"
        boolean combined,
        int attemptsCovered,
        int answeredSections,
        int answeredQuestions,
        int obtained,
        int max,
        double percentage,
        Classification overall,
        List<SectionResult> strong,
        List<SectionResult> mid,
        List<SectionResult> weak,
        List<SectionResult> all) {

    public long roundedPercentage() {
        return Math.round(percentage);
    }

    public boolean isEmpty() {
        return answeredQuestions == 0;
    }
}
