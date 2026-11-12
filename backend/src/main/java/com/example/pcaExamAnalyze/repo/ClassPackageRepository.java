package com.example.pcaExamAnalyze.repo;
import com.example.pcaExamAnalyze.domain.ClassPackage;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface ClassPackageRepository extends JpaRepository<ClassPackage, Long> {
    List<ClassPackage> findAllByOrderByDisplayOrderAscIdAsc();
    List<ClassPackage> findByPublishedTrueOrderByDisplayOrderAscIdAsc();
    Optional<ClassPackage> findFirstByOrderByDisplayOrderDescIdDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ClassPackage p where p.id = :id")
    Optional<ClassPackage> lockById(@Param("id") Long id);
}
