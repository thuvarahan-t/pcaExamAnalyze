package com.example.pcaExamAnalyze.repo;

import com.example.pcaExamAnalyze.domain.PaperStructure;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaperStructureRepository extends JpaRepository<PaperStructure, Long> {

    List<PaperStructure> findAllByOrderByYearAsc();

    Optional<PaperStructure> findByYear(Integer year);

    boolean existsByYear(Integer year);

    @EntityGraph(attributePaths = "questions")
    Optional<PaperStructure> findWithQuestionsById(Long id);
}
