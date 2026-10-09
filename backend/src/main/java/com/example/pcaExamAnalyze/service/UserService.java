package com.example.pcaExamAnalyze.service;

import com.example.pcaExamAnalyze.domain.User;
import com.example.pcaExamAnalyze.repo.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public boolean usernameTaken(String username) {
        return username != null && users.existsByUsernameIgnoreCase(username.trim());
    }

    public Optional<User> findByUsername(String username) {
        return username == null ? Optional.empty() : users.findByUsernameIgnoreCase(username.trim());
    }

    public User requireByUsername(String username) {
        return users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalStateException("User not found: " + username));
    }
}
