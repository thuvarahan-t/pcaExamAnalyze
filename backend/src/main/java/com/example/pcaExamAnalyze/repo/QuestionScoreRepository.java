package com.example.pcaExamAnalyze.repo;

import com.example.pcaExamAnalyze.domain.QuestionScore;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionScoreRepository extends JpaRepository<QuestionScore, Long> {

    @EntityGraph(attributePaths = {"question", "attempt"})
    List<QuestionScore> findByAttemptIdIn(List<Long> attemptIds);
}
