package com.example.pcaExamAnalyze.config;

import com.example.pcaExamAnalyze.domain.*;
import com.example.pcaExamAnalyze.repo.PaperStructureRepository;
import com.example.pcaExamAnalyze.repo.McqBatchRepository;
import com.example.pcaExamAnalyze.repo.SectionRepository;
import com.example.pcaExamAnalyze.repo.UserRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On startup: seeds the teacher (admin) account from the .env values, and — when
 * {@code pca.seed-sample-data=true} (dev profile) — a small explorable data set.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository users;
    private final PaperStructureRepository papers;
    private final SectionRepository sections;
    private final PasswordEncoder encoder;
    private final McqBatchRepository mcqBatches;

    @Value("${pca.teacher.username}")
    private String teacherUsername;
    @Value("${pca.teacher.password}")
    private String teacherPassword;
    @Value("${pca.teacher.name}")
    private String teacherName;
    @Value("${pca.seed-sample-data:false}")
    private boolean seedSampleData;

    public DataInitializer(UserRepository users, PaperStructureRepository papers,
                           SectionRepository sections, PasswordEncoder encoder,
                           McqBatchRepository mcqBatches) {
        this.users = users;
        this.papers = papers;
        this.sections = sections;
        this.encoder = encoder;
        this.mcqBatches = mcqBatches;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedTeacher();
        seedMcqBatches();
        if (seedSampleData) {
            seedSamplePapers();
            seedDemoStudent();
        }
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

    private void seedDemoStudent() {
        String username = "student";
        if (users.existsByUsernameIgnoreCase(username)) {
            return;
        }
        users.save(new User(username, encoder.encode("student123@"), "Demo Student",
                "+94 77 123 4567", "200321812435", "Colombo", "2026 Proper Batch", Role.STUDENT));
        log.info("Seeded demo student: {} / student123@", username);
    }

    private void seedSamplePapers() {
        if (papers.count() > 0) {
            return;
        }

        // Shared section list (reused across every year's paper).
        Map<String, Section> sectionByName = new LinkedHashMap<>();
        for (String name : new String[]{"Mechanics", "Waves", "Electricity", "Thermal Physics", "Optics"}) {
            sectionByName.put(name, sections.save(new Section(name)));
        }
        Section[] order = sectionByName.values().toArray(new Section[0]);

        for (int year = 2021; year <= 2025; year++) {
            PaperStructure paper = new PaperStructure(year, "Physics", year + " A/L Physics Past Paper");

            // Four structured questions (Q1–Q4), 20 marks each, rotating through the sections.
            for (int qn = 1; qn <= 4; qn++) {
                Section section = order[(year - 2021 + qn - 1) % order.length];
                String topic = section.getName();
                Question q = new Question(paper, "Q" + qn, section, Question.DEFAULT_MAX_MARKS,
                        "Structured question on " + topic + ".");
                q.getReferences().add(new QuestionReference(q, topic + " Notes",
                        ReferenceType.FILE, "PCA " + topic + " Notes — full chapter",
                        "Focus on worked examples."));
                q.getReferences().add(new QuestionReference(q, topic + " Concept Video",
                        ReferenceType.VIDEO, "https://example.com/pca/" + topic.toLowerCase().replace(' ', '-'),
                        "Watch before re-attempting."));
                paper.getQuestions().add(q);
            }

            papers.save(paper);
        }
        log.info("Seeded {} sections and sample papers for 2021–2025 ({} papers).",
                sectionByName.size(), papers.count());
    }
}
