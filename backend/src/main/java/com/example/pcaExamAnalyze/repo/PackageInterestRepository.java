package com.example.pcaExamAnalyze.repo;
import com.example.pcaExamAnalyze.domain.PackageInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PackageInterestRepository extends JpaRepository<PackageInterest,Long> {
 Optional<PackageInterest> findByClassPackageIdAndRegistrationId(Long packageId, String registrationId);
 Optional<PackageInterest> findByIdAndClassPackageId(Long id, Long packageId);
 Optional<PackageInterest> findByClassPackageIdAndRequestKey(Long packageId, String requestKey);
 boolean existsByClassPackageIdAndPhoneNumber(Long packageId, String phoneNumber);
 List<PackageInterest> findByClassPackageIdOrderByLastInterestedAtDescIdDesc(Long packageId);
}
