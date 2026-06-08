package com.example.pcaExamAnalyze.domain;

/**
 * Performance band for a topic / overall attempt.
 * Thresholds live in {@code AnalysisProperties} so they stay easy to tune.
 */
public enum Classification {
    WEAK("Weak", "weak", "#e5484d"),
    MID("Mid-range", "mid", "#e0930a"),
    STRONG("Strong", "strong", "#16a06a");

    private final String label;
    /** CSS modifier used by the glassmorphism theme. */
    private final String cssClass;
    /** Hex colour used by Chart.js datasets and the PDF. */
    private final String color;

    Classification(String label, String cssClass, String color) {
        this.label = label;
        this.cssClass = cssClass;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public String getCssClass() {
        return cssClass;
    }

    public String getColor() {
        return color;
    }
}
