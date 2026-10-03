package com.example.pcaExamAnalyze.repo;

import com.example.pcaExamAnalyze.domain.McqExam;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface McqExamRepository extends JpaRepository<McqExam, Long> {

    boolean existsBySlug(String slug);

    /** No entity graph: collections load lazily in small batches instead of one huge cartesian join. */
    @Query("select e from McqExam e where e.id = :id")
    Optional<McqExam> findPlainById(@Param("id") Long id);

    boolean existsBySlugAndIdNot(String slug, Long id);

    /** Collections load lazily in batches (default_batch_fetch_size); a 4-collection join multiplied rows. */
    List<McqExam> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"eligibleBatches", "eligibleStreams", "answerKey", "acceptedAnswerKeys"})
    Optional<McqExam> findById(Long id);

    @EntityGraph(attributePaths = {"eligibleBatches", "eligibleStreams", "answerKey", "acceptedAnswerKeys"})
    Optional<McqExam> findBySlug(String slug);

    /** Student pages do not need streams or answer keys; avoid the large multi-collection join. */
    @EntityGraph(attributePaths = {"eligibleBatches"})
    @Query("select distinct e from McqExam e where e.publicationState = com.example.pcaExamAnalyze.domain.McqExamPublicationState.PUBLISHED order by e.createdAt desc")
    List<McqExam> findPublishedForStudents();

    @EntityGraph(attributePaths = {"eligibleBatches"})
    @Query("select e from McqExam e where e.slug = :slug")
    Optional<McqExam> findStudentExamBySlug(@Param("slug") String slug);

    @Query("select distinct e.examYear from McqExam e where e.publicationState = com.example.pcaExamAnalyze.domain.McqExamPublicationState.PUBLISHED order by e.examYear desc")
    List<Integer> findPublishedExamYears();

    /** Deleting an exam: bulk statements instead of loading and deleting every submission row by row. */
    @org.springframework.data.jpa.repository.Modifying
    @Query(nativeQuery = true, value = "delete from mcq_submission_answers where submission_id in (select id from mcq_submissions where exam_id = :id)")
    void deleteSubmissionAnswers(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying
    @Query(nativeQuery = true, value = "delete from mcq_submission_question_times where submission_id in (select id from mcq_submissions where exam_id = :id)")
    void deleteSubmissionTimes(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying
    @Query(nativeQuery = true, value = "delete from mcq_submissions where exam_id = :id")
    void deleteSubmissions(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying
    @Query(nativeQuery = true, value = "delete from mcq_exam_batches where exam_id = :id")
    void deleteExamBatches(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying
    @Query(nativeQuery = true, value = "delete from mcq_exam_streams where exam_id = :id")
    void deleteExamStreams(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying
    @Query(nativeQuery = true, value = "delete from mcq_answer_keys where exam_id = :id")
    void deleteAnswerKeys(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying
    @Query(nativeQuery = true, value = "delete from mcq_accepted_answer_keys where exam_id = :id")
    void deleteAcceptedAnswerKeys(@Param("id") Long id);


    @org.springframework.data.jpa.repository.Modifying
    @Query(nativeQuery = true, value = "delete from mcq_exams where id = :id")
    void deleteExamRow(@Param("id") Long id);
}
