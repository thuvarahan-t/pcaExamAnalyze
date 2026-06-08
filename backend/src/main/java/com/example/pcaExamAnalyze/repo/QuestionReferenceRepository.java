package com.example.pcaExamAnalyze.repo;

import com.example.pcaExamAnalyze.domain.QuestionReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionReferenceRepository extends JpaRepository<QuestionReference, Long> {

    List<QuestionReference> findByQuestionIdOrderByIdAsc(Long questionId);

    List<QuestionReference> findByQuestionIdInOrderByIdAsc(List<Long> questionIds);

    long countByQuestionId(Long questionId);

    long countByQuestion_PaperStructureId(Long paperStructureId);
}
