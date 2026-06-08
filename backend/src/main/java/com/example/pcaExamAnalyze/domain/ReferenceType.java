package com.example.pcaExamAnalyze.domain;

public enum ReferenceType {
    FILE("Files", "bi-file-earmark-text"),
    VIDEO("Videos", "bi-play-circle"),
    PRACTICAL("Practical", "bi-clipboard-check");

    private final String label;
    private final String icon;

    ReferenceType(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }
}
