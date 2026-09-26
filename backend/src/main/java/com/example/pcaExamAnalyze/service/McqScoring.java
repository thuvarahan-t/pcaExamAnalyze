package com.example.pcaExamAnalyze.service;

import com.example.pcaExamAnalyze.domain.McqExam;
import com.example.pcaExamAnalyze.domain.McqSubmission;

import java.time.Instant;

/** Marks a submission against the exam's current answer key. */
final class McqScoring {

    private McqScoring() {}

    /** Returns false (and leaves the submission untouched) while the answer key is incomplete. */
    static boolean score(McqExam exam, McqSubmission submission) {
        if (!exam.hasCompleteAnswerKey()) return false;
        int correct = 0;
        int incorrect = 0;
        int unanswered = 0;
        for (int question = 1; question <= exam.getTotalQuestions(); question++) {
            Integer selected = submission.getAnswers().get(question);
            if (exam.isFreeMark(question) || selected != null && exam.correctOptions(question).contains(selected)) correct++;
            else if (selected == null) unanswered++;
            else incorrect++;
        }
        int answered = (int) submission.getAnswers().keySet().stream()
                .filter(q -> q >= 1 && q <= exam.getTotalQuestions()).count();
        submission.setScore(correct);
        submission.setCorrectCount(correct);
        submission.setIncorrectCount(incorrect);
        submission.setUnansweredCount(unanswered);
        submission.setPercentage(exam.getTotalQuestions() == 0 ? 0 : correct * 100.0 / exam.getTotalQuestions());
        submission.setResultCalculatedAt(Instant.now());
        return true;
    }
}
