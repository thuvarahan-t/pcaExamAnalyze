package com.example.pcaExamAnalyze.repo;

import com.example.pcaExamAnalyze.domain.McqBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface McqBatchRepository extends JpaRepository<McqBatch, Long> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    List<McqBatch> findAllByOrderByNameAsc();
    List<McqBatch> findByActiveTrueOrderByNameAsc();
}
