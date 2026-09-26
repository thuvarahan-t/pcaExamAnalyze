package com.example.pcaExamAnalyze.repo;

import com.example.pcaExamAnalyze.domain.SyllabusUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SyllabusUnitRepository extends JpaRepository<SyllabusUnit, Long> {

    List<SyllabusUnit> findAllByOrderByPositionAscIdAsc();

    @Query("select coalesce(max(u.position), 0) from SyllabusUnit u")
    int maxPosition();
}
