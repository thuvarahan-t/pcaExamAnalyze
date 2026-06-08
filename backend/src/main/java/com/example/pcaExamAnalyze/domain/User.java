package com.example.pcaExamAnalyze.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Login identifier. Replaces email — the app no longer uses email at all. */
    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String fullName;

    /** Student details collected at registration. Nullable so the teacher seed can omit them. */
    @Column(length = 20)
    private String mobile;

    /** National Identity Card number — used to verify a self-service password reset. */
    @Column(length = 20)
    private String nic;

    @Column(length = 40)
    private String district;

    @Column(length = 40)
    private String batch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    /** Kept for Spring Security; accounts are active immediately (no email verification). */
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Minimal constructor for seeded accounts (teacher, demo student). */
    public User(String username, String passwordHash, String fullName, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }

    /** Full constructor for a self-registered student. */
    public User(String username, String passwordHash, String fullName,
                String mobile, String nic, String district, String batch, Role role) {
        this(username, passwordHash, fullName, role);
        this.mobile = mobile;
        this.nic = nic;
        this.district = district;
        this.batch = batch;
    }
}
