package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.service.GeminiFeedbackTranscriber;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = ClassPackageController.class)
public class OfferFeedbackVoiceModel {
    private final GeminiFeedbackTranscriber gemini;

    public OfferFeedbackVoiceModel(GeminiFeedbackTranscriber gemini) {
        this.gemini = gemini;
    }

    @ModelAttribute("feedbackVoiceAvailable")
    public boolean feedbackVoiceAvailable() {
        return gemini.available();
    }
}
