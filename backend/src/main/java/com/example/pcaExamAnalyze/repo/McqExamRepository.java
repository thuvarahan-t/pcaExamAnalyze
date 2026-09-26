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
}
