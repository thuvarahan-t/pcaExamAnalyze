package com.example.pcaExamAnalyze.repo;

import com.example.pcaExamAnalyze.domain.Attempt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    /** All of a student's attempts, oldest first (Attempt 1, 2, 3 …). */
    List<Attempt> findByStudentIdOrderByAttemptNumberAsc(Long studentId);

    /** One attempt with its scores + each score's question eagerly loaded. */
    @EntityGraph(attributePaths = {"scores", "scores.question", "scores.question.section", "student"})
    Optional<Attempt> findWithScoresByStudentIdAndAttemptNumber(Long studentId, Integer attemptNumber);

    /** All attempts of a student with scores eagerly loaded (for the combined report). */
    @EntityGraph(attributePaths = {"scores", "scores.question", "scores.question.section"})
    List<Attempt> findWithScoresByStudentId(Long studentId);

    @Query("select coalesce(max(a.attemptNumber), 0) from Attempt a where a.student.id = :studentId")
    int findMaxAttemptNumber(Long studentId);

    long countByStudentId(Long studentId);
}
