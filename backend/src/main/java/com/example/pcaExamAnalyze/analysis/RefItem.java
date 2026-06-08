package com.example.pcaExamAnalyze.analysis;

import com.example.pcaExamAnalyze.domain.QuestionReference;

/** Detached, view-safe copy of a study reference. */
public record RefItem(String title, String typeLabel, String typeIcon,
                      String resource, String note, boolean url, boolean practice) {

    public static RefItem from(QuestionReference r) {
        return new RefItem(
                r.getTitle(),
                r.getType().getLabel(),
                r.getType().getIcon(),
                r.getResource(),
                r.getNote(),
                r.isUrl(),
                r.getType() == com.example.pcaExamAnalyze.domain.ReferenceType.PRACTICAL
        );
    }
}
