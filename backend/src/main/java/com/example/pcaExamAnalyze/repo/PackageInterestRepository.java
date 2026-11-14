package com.example.pcaExamAnalyze.repo;
import com.example.pcaExamAnalyze.domain.PackageInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PackageInterestRepository extends JpaRepository<PackageInterest,Long> {
 Optional<PackageInterest> findByClassPackageIdAndRegistrationId(Long packageId, String registrationId);
 List<PackageInterest> findByClassPackageIdOrderByLastInterestedAtDescIdDesc(Long packageId);
}
