package com.example.pcaExamAnalyze.repo;

import com.example.pcaExamAnalyze.domain.McqQuestionTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface McqQuestionTagRepository extends JpaRepository<McqQuestionTag, Long> {

    @Query("select t from McqQuestionTag t join fetch t.question q where q.exam.id = :examId order by q.questionNumber, t.position")
    List<McqQuestionTag> findByExamId(@Param("examId") Long examId);

    @Query("select t from McqQuestionTag t where t.question.id = :questionId order by t.position")
    List<McqQuestionTag> findByQuestionId(@Param("questionId") Long questionId);

    @Modifying
    @Query("delete from McqQuestionTag t where t.question.id = :questionId")
    void deleteByQuestionId(@Param("questionId") Long questionId);

    @Modifying
    @Query("delete from McqQuestionTag t where t.question.id in (select q.id from McqExamQuestion q where q.exam.id = :examId)")
    void deleteByExamId(@Param("examId") Long examId);
}
