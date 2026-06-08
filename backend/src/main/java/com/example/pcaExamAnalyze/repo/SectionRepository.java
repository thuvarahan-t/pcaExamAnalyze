package com.example.pcaExamAnalyze.repo;

import com.example.pcaExamAnalyze.domain.Section;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SectionRepository extends JpaRepository<Section, Long> {

    List<Section> findAllByOrderByNameAsc();

    Optional<Section> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
