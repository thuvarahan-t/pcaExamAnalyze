package com.example.pcaExamAnalyze.repo;
import com.example.pcaExamAnalyze.domain.PackagePurchase;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface PackagePurchaseRepository extends JpaRepository<PackagePurchase, Long> {
    long countByClassPackageId(Long id);
    boolean existsByClassPackageIdAndRegistrationId(Long id, String registrationId);
    Optional<PackagePurchase> findByClassPackageIdAndRegistrationId(Long id, String registrationId);
    Optional<PackagePurchase> findByIdAndClassPackageId(Long id, Long packageId);
    List<PackagePurchase> findByClassPackageIdOrderByConfirmedAtDescIdDesc(Long id);
    interface PackageCount { Long getPackageId(); long getTotal(); }
    @Query("select p.classPackage.id as packageId, count(p) as total from PackagePurchase p group by p.classPackage.id")
    List<PackageCount> counts();
    interface RecentPurchase { Long getId(); String getStudentName(); String getSchool(); String getDistrict();
        String getPackageName(); java.time.Instant getConfirmedAt(); }
    @Query("select p.id as id, p.studentName as studentName, p.school as school, p.district as district,"
        + " p.classPackage.name as packageName, p.confirmedAt as confirmedAt"
        + " from PackagePurchase p where p.classPackage.published = true order by p.confirmedAt desc, p.id desc")
    List<RecentPurchase> recent(org.springframework.data.domain.Pageable page);
    /** Next page of the public list: purchases strictly older than the (confirmedAt, id) cursor. */
    @Query("select p.id as id, p.studentName as studentName, p.school as school, p.district as district,"
        + " p.classPackage.name as packageName, p.confirmedAt as confirmedAt"
        + " from PackagePurchase p where p.classPackage.published = true"
        + " and (p.confirmedAt < :at or (p.confirmedAt = :at and p.id < :id))"
        + " order by p.confirmedAt desc, p.id desc")
    List<RecentPurchase> recentBefore(@org.springframework.data.repository.query.Param("at") java.time.Instant at,
        @org.springframework.data.repository.query.Param("id") Long id, org.springframework.data.domain.Pageable page);
}
