package com.example.pcaExamAnalyze.repo;

import com.example.pcaExamAnalyze.domain.McqSubImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface McqSubImageRepository extends JpaRepository<McqSubImage, Long> {
    interface Reference { Long getId(); Integer getQuestionNumber(); }
    @Query("select s.id as id, s.questionNumber as questionNumber from McqSubImage s where s.examId = :examId order by s.id")
    List<Reference> references(Long examId);
    List<McqSubImage> findByExamIdAndQuestionNumberOrderById(Long examId, int questionNumber);
    Optional<McqSubImage> findByIdAndExamIdAndQuestionNumber(Long id, Long examId, int questionNumber);
    List<McqSubImage> findByExamId(Long examId);
}
