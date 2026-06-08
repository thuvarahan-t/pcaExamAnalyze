package com.example.pcaExamAnalyze.repo;

import com.example.pcaExamAnalyze.domain.Question;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByPaperStructureIdOrderByIdAsc(Long paperStructureId);

    long countByPaperStructureId(Long paperStructureId);

    boolean existsBySectionId(Long sectionId);

    @EntityGraph(attributePaths = "paperStructure")
    Optional<Question> findWithPaperById(Long id);
}
