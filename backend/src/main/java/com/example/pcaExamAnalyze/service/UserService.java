package com.example.pcaExamAnalyze.service;

import com.example.pcaExamAnalyze.domain.Role;
import com.example.pcaExamAnalyze.domain.User;
import com.example.pcaExamAnalyze.repo.UserRepository;
import com.example.pcaExamAnalyze.web.dto.RegisterForm;
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

    /** Registers a student. Accounts are active immediately — there is no email verification. */
    @Transactional
    public User registerStudent(RegisterForm form) {
        User user = new User(
                form.getUsername().trim().toLowerCase(),
                encoder.encode(form.getPassword()),
                form.getFullName().trim(),
                form.getMobile().trim(),
                form.getNic().trim(),
                form.getDistrict().trim(),
                form.getBatch().trim(),
                Role.STUDENT);
        return users.save(user);
    }

    /**
     * Self-service reset: succeeds only when the username exists AND the supplied NIC matches.
     * Returns true if the password was changed.
     */
    @Transactional
    public boolean resetPasswordByNic(String username, String nic, String newPassword) {
        if (username == null || nic == null) {
            return false;
        }
        return users.findByUsernameIgnoreCaseAndNicIgnoreCase(username.trim(), nic.trim())
                .map(u -> {
                    u.setPasswordHash(encoder.encode(newPassword));
                    users.save(u);
                    return true;
                })
                .orElse(false);
    }

    public User requireByUsername(String username) {
        return users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalStateException("User not found: " + username));
    }
}
