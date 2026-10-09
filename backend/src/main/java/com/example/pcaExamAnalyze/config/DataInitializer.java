package com.example.pcaExamAnalyze.config;

import com.example.pcaExamAnalyze.domain.*;
import com.example.pcaExamAnalyze.repo.McqBatchRepository;
import com.example.pcaExamAnalyze.repo.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On startup: seeds the teacher (admin) account from the .env values and the default MCQ batches.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final McqBatchRepository mcqBatches;

    @Value("${pca.teacher.username}")
    private String teacherUsername;
    @Value("${pca.teacher.password}")
    private String teacherPassword;
    @Value("${pca.teacher.name}")
    private String teacherName;

    public DataInitializer(UserRepository users, PasswordEncoder encoder,
                           McqBatchRepository mcqBatches) {
        this.users = users;
        this.encoder = encoder;
        this.mcqBatches = mcqBatches;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedTeacher();
        seedMcqBatches();
    }

    private void seedMcqBatches() {
        if (mcqBatches.count() > 0) return;
        for (String name : new String[]{"2026 A/L", "2027 A/L", "2028 A/L", "Repeat Batch"}) {
            mcqBatches.save(new McqBatch(name));
        }
        log.info("Seeded default PCA MCQ batches");
    }

    private void seedTeacher() {
        User existing = users.findByUsernameIgnoreCase(teacherUsername).orElse(null);
        if (existing != null) {
            boolean changed = false;
            if (!encoder.matches(teacherPassword, existing.getPasswordHash())) {
                existing.setPasswordHash(encoder.encode(teacherPassword));
                changed = true;
            }
            if (!teacherName.equals(existing.getFullName())) {
                existing.setFullName(teacherName);
                changed = true;
            }
            if (existing.getRole() != Role.TEACHER) {
                existing.setRole(Role.TEACHER);
                changed = true;
            }
            if (!existing.isEnabled()) {
                existing.setEnabled(true);
                changed = true;
            }
            if (changed) {
                users.save(existing);
                log.info("Synchronized teacher account from environment: {}", teacherUsername);
            } else {
                log.info("Teacher account already synchronized: {}", teacherUsername);
            }
            return;
        }
        User teacher = new User(teacherUsername, encoder.encode(teacherPassword), teacherName, Role.TEACHER);
        users.save(teacher);
        log.info("Seeded teacher account: {}", teacherUsername);
    }
}
