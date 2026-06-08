package com.example.pcaExamAnalyze.service;

import com.example.pcaExamAnalyze.analysis.RefItem;
import com.example.pcaExamAnalyze.analysis.ReportData;
import com.example.pcaExamAnalyze.analysis.SectionResult;
import com.example.pcaExamAnalyze.config.AnalysisProperties;
import com.example.pcaExamAnalyze.domain.*;
import com.example.pcaExamAnalyze.repo.AttemptRepository;
import com.example.pcaExamAnalyze.repo.QuestionReferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds a student's report from the marks they entered: per-section strong / mid /
 * weak bands (teacher's thresholds) plus the teacher's resources to fix weak areas.
 * Only questions the student actually answered are counted — blanks are ignored.
 */
@Service
public class StudentReportService {

    private final AttemptRepository attempts;
    private final QuestionReferenceRepository references;
    private final AnalysisProperties props;

    public StudentReportService(AttemptRepository attempts, QuestionReferenceRepository references,
                                AnalysisProperties props) {
        this.attempts = attempts;
        this.references = references;
        this.props = props;
    }

    /** Report for a single attempt. */
    @Transactional(readOnly = true)
    public ReportData forAttempt(Long studentId, int attemptNumber) {
        List<QuestionScore> scores = attempts
                .findWithScoresByStudentIdAndAttemptNumber(studentId, attemptNumber)
                .map(Attempt::getScores).orElse(List.of());
        return build("Attempt " + attemptNumber, false, 1, scores);
    }

    /** Combined report over every attempt the student has saved. */
    @Transactional(readOnly = true)
    public ReportData forAllAttempts(Long studentId) {
        List<Attempt> all = attempts.findWithScoresByStudentId(studentId);
        List<QuestionScore> scores = all.stream().flatMap(a -> a.getScores().stream()).toList();
        return build("All attempts", true, all.size(), scores);
    }

    private ReportData build(String scopeLabel, boolean combined, int attemptsCovered, List<QuestionScore> scores) {
        if (scores.isEmpty()) {
            return new ReportData(scopeLabel, combined, attemptsCovered, 0, 0, 0, 0, 0.0,
                    Classification.WEAK, List.of(), List.of(), List.of(), List.of());
        }

        // Group entered scores by section, preserving first-seen order.
        Map<String, List<QuestionScore>> bySection = scores.stream()
                .collect(Collectors.groupingBy(s -> s.getQuestion().getSection().getName(),
                        LinkedHashMap::new, Collectors.toList()));

        List<SectionResult> all = new ArrayList<>();
        int totalObtained = 0, totalMax = 0;

        for (var entry : bySection.entrySet()) {
            List<QuestionScore> group = entry.getValue();
            int obtained = group.stream().mapToInt(QuestionScore::getMarksObtained).sum();
            int max = group.stream().mapToInt(s -> s.getQuestion().getMaxMarks()).sum();
            double pct = max == 0 ? 0.0 : (obtained * 100.0) / max;
            Classification cls = props.classify(pct);

            // Distinct questions answered in this section, for resources + count.
            List<Long> questionIds = group.stream().map(s -> s.getQuestion().getId()).distinct().toList();
            List<RefItem> resources = references.findByQuestionIdInOrderByIdAsc(questionIds).stream()
                    .map(RefItem::from).toList();

            all.add(new SectionResult(entry.getKey(), AttemptService.colorFor(entry.getKey()),
                    obtained, max, pct, cls, questionIds.size(), resources));

            totalObtained += obtained;
            totalMax += max;
        }

        // Weakest first overall.
        all.sort(Comparator.comparingDouble(SectionResult::percentage));

        List<SectionResult> weak = all.stream().filter(s -> s.classification() == Classification.WEAK).toList();
        List<SectionResult> mid = all.stream().filter(s -> s.classification() == Classification.MID).toList();
        List<SectionResult> strong = all.stream().filter(s -> s.classification() == Classification.STRONG).toList();

        double overallPct = totalMax == 0 ? 0.0 : (totalObtained * 100.0) / totalMax;
        int answeredQuestions = (int) scores.stream().map(s -> s.getQuestion().getId()).count();

        return new ReportData(scopeLabel, combined, attemptsCovered, all.size(), answeredQuestions,
                totalObtained, totalMax, overallPct, props.classify(overallPct), strong, mid, weak, all);
    }
}
