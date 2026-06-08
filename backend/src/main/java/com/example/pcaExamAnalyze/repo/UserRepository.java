package com.example.pcaExamAnalyze.repo;

import com.example.pcaExamAnalyze.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);

    /** Used by the self-service password reset: the NIC must match the account. */
    Optional<User> findByUsernameIgnoreCaseAndNicIgnoreCase(String username, String nic);
}
