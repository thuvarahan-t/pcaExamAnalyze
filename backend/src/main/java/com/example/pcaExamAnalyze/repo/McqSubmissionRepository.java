package com.example.pcaExamAnalyze.repo;

import com.example.pcaExamAnalyze.domain.McqSubmission;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface McqSubmissionRepository extends JpaRepository<McqSubmission, Long> {

    long countByExamId(Long examId);

    @EntityGraph(attributePaths = {"exam", "answers"})
    List<McqSubmission> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"exam", "answers"})
    List<McqSubmission> findByExamIdOrderByCreatedAtDesc(Long examId);

    @EntityGraph(attributePaths = {"exam", "answers", "exam.answerKey", "exam.acceptedAnswerKeys", "exam.eligibleBatches"})
    java.util.Optional<McqSubmission> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"exam", "answers"})
    java.util.Optional<McqSubmission> findFirstByExamIdAndRegistrationIdIgnoreCaseAndNicOrderByCreatedAtDesc(
            Long examId, String registrationId, String nic);

    @EntityGraph(attributePaths = {"exam", "answers"})
    List<McqSubmission> findByRegistrationIdIgnoreCaseAndNicOrderByCreatedAtDesc(String registrationId, String nic);

    /** Lightweight result dashboard query: cards need exam metadata, not every answer row. */
    @EntityGraph(attributePaths = {"exam"})
    List<McqSubmission> findByRegistrationIdIgnoreCaseAndNicAndStatusOrderBySubmittedAtDesc(
            String registrationId, String nic,
            com.example.pcaExamAnalyze.domain.McqSubmissionStatus status);

    @Query("select distinct s.exam.id from McqSubmission s where upper(s.registrationId) = upper(:registrationId) and s.nic = :nic and s.status = com.example.pcaExamAnalyze.domain.McqSubmissionStatus.SUBMITTED")
    Set<Long> findSubmittedExamIds(@Param("registrationId") String registrationId, @Param("nic") String nic);

    boolean existsByExamIdAndRegistrationIdIgnoreCaseAndNicAndStatus(
            Long examId, String registrationId, String nic,
            com.example.pcaExamAnalyze.domain.McqSubmissionStatus status);

    @EntityGraph(attributePaths = {"exam", "answers"})
    @Query("select s from McqSubmission s where s.id = :id")
    java.util.Optional<McqSubmission> findStudentSessionById(@Param("id") Long id);

}
