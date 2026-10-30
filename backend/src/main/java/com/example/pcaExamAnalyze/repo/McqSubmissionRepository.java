package com.example.pcaExamAnalyze.repo;

import com.example.pcaExamAnalyze.domain.McqSubmission;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface McqSubmissionRepository extends JpaRepository<McqSubmission, Long> {

    long countByExamId(Long examId);

    interface ExamCount { Long getExamId(); long getTotal(); }

    /** Submission count of every exam in one query (the exam list used one query per exam). */
    @Query("select s.exam.id as examId, count(s) as total from McqSubmission s group by s.exam.id")
    List<ExamCount> countAllByExam();

    /** Admin dashboard: papers submitted since a moment, per exam, counted in the database. */
    @Query("select s.exam.id as examId, count(s) as total from McqSubmission s where s.submittedAt >= :since group by s.exam.id")
    List<ExamCount> countSubmittedSinceByExam(@Param("since") java.time.Instant since);

    /** Admin list and CSV rows: exam metadata only, never the answer collections. */
    @EntityGraph(attributePaths = {"exam"})
    List<McqSubmission> findAllByOrderByCreatedAtDescIdDesc();

    @EntityGraph(attributePaths = {"exam"})
    List<McqSubmission> findRowsByExamIdOrderByCreatedAtDescIdDesc(Long examId);

    /**
     * One page of the admin submission list, limited in the database. Only the to-one exam is fetched, so
     * LIMIT/OFFSET apply to submission rows. Flags stand in for optional filters (a null parameter is untyped in PostgreSQL).
     */
    @Query(value = "select s from McqSubmission s join fetch s.exam e"
            + " where (:allExams = true or e.id = :examId)"
            + " and (lower(s.studentName) like :pattern escape '!' or lower(s.registrationId) like :pattern escape '!')"
            + " and (:fromSet = false or s.submittedAt >= :from) and (:toSet = false or s.submittedAt < :to)"
            + " order by s.createdAt desc, s.id desc",
            countQuery = "select count(s) from McqSubmission s"
            + " where (:allExams = true or s.exam.id = :examId)"
            + " and (lower(s.studentName) like :pattern escape '!' or lower(s.registrationId) like :pattern escape '!')"
            + " and (:fromSet = false or s.submittedAt >= :from) and (:toSet = false or s.submittedAt < :to)")
    org.springframework.data.domain.Page<McqSubmission> findSubmissionPage(
            @Param("allExams") boolean allExams, @Param("examId") Long examId, @Param("pattern") String pattern,
            @Param("fromSet") boolean fromSet, @Param("from") java.time.Instant from,
            @Param("toSet") boolean toSet, @Param("to") java.time.Instant to,
            org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = {"exam", "answers"})
    List<McqSubmission> findByExamIdOrderByCreatedAtDesc(Long examId);

    @EntityGraph(attributePaths = {"exam", "answers", "exam.answerKey", "exam.acceptedAnswerKeys", "exam.eligibleBatches"})
    java.util.Optional<McqSubmission> findDetailedById(Long id);

    /**
     * Latest paper of a student for an exam. Fetches only the to-one exam so the LIMIT 1 runs in the database
     * (fetching the answers here made Hibernate paginate in memory, HHH90003004); answers load lazily when read.
     */
    @EntityGraph(attributePaths = {"exam"})
    java.util.Optional<McqSubmission> findFirstByExamIdAndRegistrationIdIgnoreCaseAndNicOrderByCreatedAtDescIdDesc(
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

    /**
     * Serializes a student's final submission with its individual answer autosaves.
     * Lock the parent row FIRST, and only then load the mutable answer collection.
     * Do not join-fetch a collection in this FOR UPDATE query (PostgreSQL disallows
     * locking the nullable side of an outer join).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from McqSubmission s where s.id = :id")
    java.util.Optional<McqSubmission> findByIdForUpdate(@Param("id") Long id);

    /** In-progress papers of exams with a time limit; the caller checks which are past their deadline. */
    @EntityGraph(attributePaths = {"exam"})
    @Query("select s from McqSubmission s where s.status = com.example.pcaExamAnalyze.domain.McqSubmissionStatus.IN_PROGRESS "
            + "and s.exam.durationMinutes is not null and s.exam.durationMinutes > 0 and s.startedAt is not null")
    List<McqSubmission> findTimedInProgress();

}
