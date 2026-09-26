package com.example.pcaExamAnalyze.domain;

/** How students answer an MCQ exam. */
public enum McqSheetType {
    /** Google Drive PDF paper beside a 50-row OMR-style answer sheet. */
    ANSWER_SHEET,
    /** One uploaded image per question, answered one question at a time. */
    QUESTION_IMAGES
}
