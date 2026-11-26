package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.service.OfferFeedbackTranscriber;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = ClassPackageController.class)
public class OfferFeedbackVoiceModel {
    private final OfferFeedbackTranscriber transcriber;

    public OfferFeedbackVoiceModel(OfferFeedbackTranscriber transcriber) {
        this.transcriber = transcriber;
    }

    @ModelAttribute("feedbackVoiceAvailable")
    public boolean feedbackVoiceAvailable() {
        return transcriber.available();
    }
}
