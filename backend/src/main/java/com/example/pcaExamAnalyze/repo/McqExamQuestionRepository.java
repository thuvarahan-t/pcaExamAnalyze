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

    /** Duplicating an exam: one server-side statement per table instead of a round trip per row. */
    @Modifying
    @Query(nativeQuery = true, value = "insert into mcq_exam_questions (exam_id, question_number, content_type, data, storage_key, "
            + "image_updated_at, weight_score, time_seconds, units_text, competency_levels_text, contents_text, outcomes_text, updated_at) "
            + "select :targetId, question_number, content_type, data, storage_key, image_updated_at, weight_score, time_seconds, "
            + "units_text, competency_levels_text, contents_text, outcomes_text, current_timestamp "
            + "from mcq_exam_questions where exam_id = :sourceId")
    void copyQuestions(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);

    @Modifying
    @Query(nativeQuery = true, value = "insert into mcq_question_syllabus_tags (question_id, position, unit_id, level_id, unit_name, "
            + "competency, level_name, content_paths, outcomes) "
            + "select nq.id, t.position, t.unit_id, t.level_id, t.unit_name, t.competency, t.level_name, t.content_paths, t.outcomes "
            + "from mcq_question_syllabus_tags t join mcq_exam_questions q on q.id = t.question_id "
            + "join mcq_exam_questions nq on nq.exam_id = :targetId and nq.question_number = q.question_number "
            + "where q.exam_id = :sourceId")
    void copyTags(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);

    @Modifying
    @Query(nativeQuery = true, value = "insert into mcq_question_sub_images (exam_id, question_number, content_type, storage_key, data) "
            + "select :targetId, question_number, content_type, storage_key, data from mcq_question_sub_images "
            + "where exam_id = :sourceId order by id")
    void copySubImages(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);

    /** R2 objects are shared between an exam and its duplicates, so only delete ones nothing references. */
    @Query(nativeQuery = true, value = "select storage_key from mcq_exam_questions where storage_key in (:keys) "
            + "union select storage_key from mcq_question_sub_images where storage_key in (:keys)")
    List<String> findReferencedKeys(@Param("keys") java.util.Collection<String> keys);

    @Query(nativeQuery = true, value = "select storage_key from mcq_question_sub_images where exam_id = :examId and storage_key is not null")
    List<String> findSubImageKeysByExamId(@Param("examId") Long examId);

    @Modifying
    @Query(nativeQuery = true, value = "delete from mcq_question_sub_images where exam_id = :examId")
    void deleteSubImagesByExamId(@Param("examId") Long examId);

    @Modifying
    @Query("delete from McqExamQuestion q where q.exam.id = :examId")
    void deleteByExamId(@Param("examId") Long examId);
}
