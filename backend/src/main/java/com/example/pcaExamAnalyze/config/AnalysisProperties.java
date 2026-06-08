package com.example.pcaExamAnalyze.config;

import com.example.pcaExamAnalyze.domain.Classification;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Topic classification thresholds — single place to tune the weak/mid/strong bands.
 * Configured via {@code pca.analysis.*} in application.properties.
 */
@Component
@ConfigurationProperties(prefix = "pca.analysis")
public class AnalysisProperties {

    /** Topic percentage strictly below this is WEAK. */
    private double weakBelow = 50.0;

    /** Topic percentage at or above this is STRONG. */
    private double strongAtLeast = 75.0;

    public double getWeakBelow() {
        return weakBelow;
    }

    public void setWeakBelow(double weakBelow) {
        this.weakBelow = weakBelow;
    }

    public double getStrongAtLeast() {
        return strongAtLeast;
    }

    public void setStrongAtLeast(double strongAtLeast) {
        this.strongAtLeast = strongAtLeast;
    }

    public Classification classify(double percentage) {
        if (percentage < weakBelow) {
            return Classification.WEAK;
        }
        if (percentage < strongAtLeast) {
            return Classification.MID;
        }
        return Classification.STRONG;
    }
}
