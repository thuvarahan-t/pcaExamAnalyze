package com.example.pcaExamAnalyze.report;

import java.util.List;

/**
 * Everything the exam analysis PDF shows. Built by {@link ExamReportService}; all figures that are
 * displayed are pre-formatted strings so the template contains no number formatting logic.
 */
public record ExamReport(
        Meta meta,
        Summary summary,
        List<Band> histogram,
        List<GradeRow> grades,
        List<String> insights,
        List<String> warnings,
        List<Pair> examDetails,
        List<Pair> highlights,
        List<Group> byBatch,
        List<Group> byStream,
        TimeStats timeStats,
        List<Band> timeBuckets,
        List<Band> timeline,
        List<DistrictRow> districts,
        List<String> missingDistricts,
        List<QuestionRow> questions,
        List<UnitRow> units,
        List<QuestionRow> hardest,
        List<QuestionRow> easiest,
        List<StudentRow> ranking) {

    public record Meta(String examName, String period, String batches, String streams, String generatedAt,
                       String logo, String banner, int totalQuestions, int participants, boolean hasData,
                       String instructions) {}

    public record Pair(String label, String value) {}

    /** Pre-shaped text embedded as an image (used for Tamil, which the PDF renderer cannot shape). */
    public record Img(String uri, String heightPt) {}

    public record Summary(int participants, int inProgress, String meanMarks, String meanPct, String medianPct,
                          String stdDev, String highest, String highestPct, String highestName, String lowest,
                          String lowestPct, String passRate, int passCount, String avgTime) {}

    /** One bar of a chart. {@code size} is a 0-100 percentage of the tallest bar. */
    public record Band(String label, int count, String share, int size) {}

    public record GradeRow(String grade, String range, int count, String share, int size, String color) {}

    public record Group(String name, int count, String share, String avgPct, int size, Img nameImg) {}

    public record TimeStats(String average, String fastest, String slowest, String allotted, boolean hasData) {}

    public record DistrictRow(int position, String name, int count, String share, String avgMarks, String avgPct,
                              String highest, String lowest, String passRate, int size) {}

    public record OptionRow(int option, int count, String pct, int size, boolean correct, boolean topWrong) {}

    public record TagRow(String unit, String level, String competency, List<String> contents, List<String> outcomes) {}

    public record QuestionRow(int number, String anchor, int correctCount, int answeredCount, int unansweredCount,
                              int participants, String correctPct, int correctSize, String attemptedPct,
                              String difficulty, String difficultyClass, String correctAnswer,
                              List<OptionRow> options, OptionRow unanswered, String topWrong,
                              String discrimination, String discriminationLabel, String discriminationClass,
                              String topGroupPct, String midGroupPct, String bottomGroupPct,
                              String weight, int weightSize, String expectedTime, String averageTime,
                              String timeVerdict, boolean freeMark, List<TagRow> tags, String insight,
                              String image, List<String> subImages, String flag) {}

    public record UnitRow(String unit, String questions, int questionCount, String correctPct, int size,
                          String level, String levelClass) {}

    public record StudentRow(int rank, String name, String registrationId, String district, String batch,
                             String school, int correct, int wrong, int blank, String marks, String pct,
                             String timeTaken, String submitted, String gradeLetter, Img nameImg) {}
}
