package com.example.pcaExamAnalyze.domain;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
@Entity
@Table(name="package_interests", uniqueConstraints=@UniqueConstraint(columnNames={"package_id","registration_id"}))
@Getter @Setter
public class PackageInterest {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false)
 @JoinColumn(name="package_id", nullable=false) private ClassPackage classPackage;
 @Column(name="registration_id", length=30) private String registrationId;
 @Column(length = 74) private String firstName;
 @Column(length = 75) private String lastName;
 @Column(length = 10) private String phoneNumber;
 @Column(length = 36) private String requestKey;
 public String getReference() { return "PCA-" + id; }
 @Column(nullable=false, length=150) private String studentName;
 @Column(nullable=false) private Instant firstInterestedAt = Instant.now();
 @Column(nullable=false) private Instant lastInterestedAt = Instant.now();
}
