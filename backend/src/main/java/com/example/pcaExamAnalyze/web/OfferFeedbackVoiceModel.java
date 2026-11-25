package com.example.pcaExamAnalyze.web;

import com.example.pcaExamAnalyze.service.GroqFeedbackTranscriber;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = ClassPackageController.class)
public class OfferFeedbackVoiceModel {
    private final GroqFeedbackTranscriber groq;

    public OfferFeedbackVoiceModel(GroqFeedbackTranscriber groq) {
        this.groq = groq;
    }

    @ModelAttribute("feedbackVoiceAvailable")
    public boolean feedbackVoiceAvailable() {
        return groq.available();
    }
}
