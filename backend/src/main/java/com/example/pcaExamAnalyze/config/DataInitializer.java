package com.example.pcaExamAnalyze.config;

import com.example.pcaExamAnalyze.domain.*;
import com.example.pcaExamAnalyze.repo.PaperStructureRepository;
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

    @Value("${pca.teacher.username}")
    private String teacherUsername;
    @Value("${pca.teacher.password}")
    private String teacherPassword;
    @Value("${pca.teacher.name}")
    private String teacherName;
    @Value("${pca.seed-sample-data:false}")
    private boolean seedSampleData;

    public DataInitializer(UserRepository users, PaperStructureRepository papers,
                           SectionRepository sections, PasswordEncoder encoder) {
        this.users = users;
        this.papers = papers;
        this.sections = sections;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedTeacher();
        if (seedSampleData) {
            seedSamplePapers();
            seedDemoStudent();
        }
    }

    private void seedTeacher() {
        if (users.existsByUsernameIgnoreCase(teacherUsername)) {
            log.info("Teacher account already present: {}", teacherUsername);
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
