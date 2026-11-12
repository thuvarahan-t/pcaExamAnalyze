package com.example.pcaExamAnalyze.repo;
import com.example.pcaExamAnalyze.domain.PackagePurchase;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface PackagePurchaseRepository extends JpaRepository<PackagePurchase, Long> {
    long countByClassPackageId(Long id);
    boolean existsByClassPackageIdAndRegistrationId(Long id, String registrationId);
    Optional<PackagePurchase> findByIdAndClassPackageId(Long id, Long packageId);
    List<PackagePurchase> findByClassPackageIdOrderByConfirmedAtDescIdDesc(Long id);
    interface PackageCount { Long getPackageId(); long getTotal(); }
    @Query("select p.classPackage.id as packageId, count(p) as total from PackagePurchase p group by p.classPackage.id")
    List<PackageCount> counts();
}
