package com.example.pcaExamAnalyze.service;

import com.example.pcaExamAnalyze.domain.McqExam;
import com.example.pcaExamAnalyze.domain.McqExamQuestion;
import com.example.pcaExamAnalyze.domain.McqSheetType;
import com.example.pcaExamAnalyze.domain.McqSubmission;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Business-rule regressions for exam marking, accepted keys, free marks and teacher metadata. */
class McqScoringAndExamRulesTest {
    private McqExam exam(int questions) {
        var exam = new McqExam();
        exam.setTotalQuestions(questions);
        exam.setOptionsPerQuestion(5);
        return exam;
    }

    @Test void incompleteKeyDoesNotPublishPartialScoreOrOverwritePreviousScore() {
        var e = exam(3);
        e.getAnswerKey().put(1, 1);
        var paper = new McqSubmission();
        paper.setScore(99);
        paper.setPercentage(99.0);
        paper.getAnswers().put(1, 1);
        assertFalse(e.hasCompleteAnswerKey());
        assertEquals(1, e.answerKeyCount());
        assertFalse(McqScoring.score(e, paper));
        assertEquals(99, paper.getScore());
        assertEquals(99.0, paper.getPercentage());
        assertNull(paper.getResultCalculatedAt());
    }

    @Test void acceptedAlternativesFreeMarkWrongAndBlankAreAllScored() {
        var e = exam(4);
        e.setCorrectOptionsFor(1, List.of(1));
        e.setCorrectOptionsFor(2, List.of(3, 1, 3));
        e.setCorrectOptionsFor(3, List.of(2));
        e.setCorrectOptionsFor(4, List.of(1));
        e.setFreeMark(4, true);
        var s = new McqSubmission();
        s.getAnswers().putAll(Map.of(1, 4, 2, 3, 4, 5));
        assertTrue(e.hasCompleteAnswerKey());
        assertTrue(McqScoring.score(e, s));
        assertEquals(2, s.getScore());
        assertEquals(2, s.getCorrectCount());
        assertEquals(1, s.getIncorrectCount());
        assertEquals(1, s.getUnansweredCount());
        assertEquals(50.0, s.getPercentage());
        assertNotNull(s.getResultCalculatedAt());
        assertEquals(Set.of(1, 3), e.correctOptions(2));
    }

    @Test void legacyAcceptedKeyFallbackIgnoresMalformedAndOutOfRangeOptions() {
        var e = exam(2);
        e.getAnswerKey().put(1, 2);
        e.getAcceptedAnswerKeys().put(1, "99,xyz,-1, 3, 3");
        e.getAcceptedAnswerKeys().put(2, "nope");
        e.getAnswerKey().put(2, 99);
        assertEquals(Set.of(3), e.correctOptions(1));
        assertTrue(e.correctOptions(2).isEmpty());
        assertEquals(Map.of(1, List.of(3)), e.correctAnswerOptions());
        e.getAcceptedAnswerKeys().put(2, "  ");
        e.getAnswerKey().put(2, 1);
        assertEquals(Set.of(1), e.correctOptions(2));
        assertTrue(e.hasCompleteAnswerKey());
        e.getAnswerKey().put(2, 8);
        assertFalse(e.hasCompleteAnswerKey());
    }

    @Test void freeMarksCanBeAddedAndRemovedWithoutChangingOtherQuestions() {
        var e = exam(5);
        assertFalse(e.isFreeMark(1));
        e.setFreeMark(2, true);
        e.setFreeMark(4, true);
        e.setFreeMark(2, true);
        assertEquals("2,4", e.getFreeMarkQuestions());
        assertTrue(e.isFreeMark(4));
        e.setFreeMark(2, false);
        assertEquals("4", e.getFreeMarkQuestions());
        e.setFreeMark(4, false);
        assertEquals("", e.getFreeMarkQuestions());
        assertFalse(e.isFreeMark(4));
    }

    @Test void keysAreUpdatedIndividuallyWithoutDeletingUnrelatedKeys() {
        var e = exam(4);
        e.setCorrectOptionsFor(1, List.of(2, 2, 99, null, 3));
        e.setCorrectOptionsFor(2, List.of(5));
        assertEquals("2,3", e.getAcceptedAnswerKeys().get(1));
        e.setCorrectOptionsFor(1, Arrays.asList(null, -1, 6));
        assertFalse(e.getAcceptedAnswerKeys().containsKey(1));
        assertEquals(Set.of(5), e.correctOptions(2));
        e.setCorrectOptionsFor(2, null);
        assertTrue(e.getAnswerKey().isEmpty());
        e.setCorrectAnswerOptions(Map.of(1, List.of(1, 4), 3, List.of(2)));
        assertEquals(Map.of(1, 1, 3, 2), e.getAnswerKey());
        e.setCorrectAnswerOptions(Map.of(3, List.of(2, 2)));
        assertEquals(Set.of(3), e.getAnswerKey().keySet());
        e.setCorrectAnswerOptions(null);
        assertTrue(e.getAnswerKey().isEmpty());
        assertTrue(e.getAcceptedAnswerKeys().isEmpty());
    }

    @Test void zeroQuestionExamAndInvalidAnswerKeysHaveDefinedBehavior() {
        var e = exam(0);
        assertTrue(e.hasCompleteAnswerKey());
        var paper = new McqSubmission();
        paper.getAnswers().put(-1, 2);
        paper.getAnswers().put(900, 1);
        assertTrue(McqScoring.score(e, paper));
        assertEquals(0, paper.getScore());
        assertEquals(0.0, paper.getPercentage());
        assertEquals(0, paper.getUnansweredCount());
        assertEquals(McqSheetType.ANSWER_SHEET, e.effectiveSheetType());
        e.setSheetType(null);
        assertEquals(McqSheetType.ANSWER_SHEET, e.effectiveSheetType());
        e.setSheetType(McqSheetType.QUESTION_IMAGES);
        assertTrue(e.usesQuestionImages());
    }

    @Test void questionMetadataNormalizersRejectInvalidValues() {
        assertEquals(List.of(), McqExamQuestion.splitList(null));
        assertEquals(List.of(), McqExamQuestion.splitList(" \n \t "));
        assertEquals(List.of("Mechanics", "Thermal"), McqExamQuestion.splitList("Mechanics\n\n  Thermal "));
        assertNull(McqExamQuestion.joinList(null, 20, 100));
        assertNull(McqExamQuestion.joinList(List.of("   "), 20, 100));
        assertEquals("Kinematics\nHeat", McqExamQuestion.joinList(List.of(" Kinematics ", "Heat", "Heat"), 20, 100));
        assertEquals("ABCDE", McqExamQuestion.joinList(List.of("ABCDEFG"), 5, 20));
        assertEquals("A", McqExamQuestion.joinList(List.of("A", "BBBBBBBB"), 10, 3));
        assertNull(McqExamQuestion.normalizeWeight(null));
        assertNull(McqExamQuestion.normalizeWeight(Double.NaN));
        assertNull(McqExamQuestion.normalizeWeight(0.99));
        assertNull(McqExamQuestion.normalizeWeight(5.01));
        assertEquals(3.5, McqExamQuestion.normalizeWeight(3.3));
        assertEquals("", McqExamQuestion.weightText(null));
        assertEquals("3", McqExamQuestion.weightText(3.0));
        assertEquals("3.5", McqExamQuestion.weightText(3.5));
        assertEquals(0, McqExamQuestion.fullStars(null));
        assertEquals(3, McqExamQuestion.fullStars(3.5));
        assertTrue(McqExamQuestion.halfStar(3.5));
        assertFalse(McqExamQuestion.halfStar(null));
        assertFalse(McqExamQuestion.halfStar(3.0));
        assertEquals("#94a3b8", McqExamQuestion.weightColor(null));
        assertEquals("hsl(135 80% 40%)", McqExamQuestion.weightColor(1.0));
        assertEquals("hsl(0 80% 40%)", McqExamQuestion.weightColor(5.0));
        assertEquals("-", McqExamQuestion.formatTime(null));
        assertEquals("-", McqExamQuestion.formatTime(0));
        assertEquals("9 sec", McqExamQuestion.formatTime(9));
        assertEquals("2 min", McqExamQuestion.formatTime(120));
        assertEquals("2 min 9 sec", McqExamQuestion.formatTime(129));
        var image = new McqExamQuestion();
        assertFalse(image.hasImage());
        image.setData(new byte[]{1});
        assertTrue(image.hasImage());
        image.setData(new byte[0]);
        image.setStorageKey("objects/questions/q1.png");
        assertTrue(image.hasImage());
    }
}
