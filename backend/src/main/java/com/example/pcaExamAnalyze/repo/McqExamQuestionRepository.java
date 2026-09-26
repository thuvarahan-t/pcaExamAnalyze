package com.example.pcaExamAnalyze.repo;

import com.example.pcaExamAnalyze.domain.McqExamQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface McqExamQuestionRepository extends JpaRepository<McqExamQuestion, Long> {

    Optional<McqExamQuestion> findByExamIdAndQuestionNumber(Long examId, Integer questionNumber);

    /** Everything except the image bytes, so listing a 50-question paper stays cheap. */
    @Query("select new com.example.pcaExamAnalyze.repo.McqQuestionMeta("
            + "q.questionNumber, q.imageUpdatedAt, q.weight, q.timeSeconds, q.units, q.competencyLevels, q.contents, q.learningOutcomes, q.storageKey) "
            + "from McqExamQuestion q where q.exam.id = :examId order by q.questionNumber")
    List<McqQuestionMeta> findMetaByExamId(@Param("examId") Long examId);

    @Query("select q.storageKey from McqExamQuestion q where q.exam.id = :examId and q.storageKey is not null")
    List<String> findStorageKeysByExamId(@Param("examId") Long examId);

    /** Rows whose image is still in the database, for the one-time move to R2. */
    @Query("select q.id from McqExamQuestion q where q.data is not null and q.storageKey is null")
    List<Long> findIdsWithDatabaseImages();

    interface KeyRef { Integer getQuestionNumber(); String getStorageKey(); }

    @Query("select q.questionNumber as questionNumber, q.storageKey as storageKey from McqExamQuestion q "
            + "where q.exam.id = :examId and q.storageKey is not null")
    List<KeyRef> findKeyRefsByExamId(@Param("examId") Long examId);

    @Modifying
    @Query("delete from McqExamQuestion q where q.exam.id = :examId")
    void deleteByExamId(@Param("examId") Long examId);
}
