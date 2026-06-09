package com.example.pcaExamAnalyze.repo;

import com.example.pcaExamAnalyze.domain.QuestionScore;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionScoreRepository extends JpaRepository<QuestionScore, Long> {

    @EntityGraph(attributePaths = {"question", "attempt"})
    List<QuestionScore> findByAttemptIdIn(List<Long> attemptIds);

    /** Remove every student score for a question (used when a teacher deletes/clears it). */
    void deleteByQuestionId(Long questionId);

    /** Remove every student score belonging to any question of a paper (used when a paper is deleted). */
    void deleteByQuestion_PaperStructureId(Long paperId);
}
