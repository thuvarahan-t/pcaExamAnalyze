package com.example.pcaExamAnalyze.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

/**
 * One question of a {@link McqSheetType#QUESTION_IMAGES} exam: its image (stored in the
 * database so it survives redeploys) plus teacher-only metadata used for analysis.
 */
@Entity
@Table(name = "mcq_exam_questions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"exam_id", "question_number"}))
@Getter
@Setter
@NoArgsConstructor
public class McqExamQuestion {

    public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    /** Expected answering time choices: 30 s steps up to 6 minutes. */
    public static final List<Integer> TIME_OPTIONS = IntStream.rangeClosed(1, 12).map(i -> i * 30).boxed().toList();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private McqExam exam;

    @Column(name = "question_number", nullable = false)
    private Integer questionNumber;

    @Column(length = 60)
    private String contentType;

    /** Legacy/fallback storage (R2 not configured). bytea on Postgres; H2 accepts it as a VARBINARY alias, so no BLOB/OID handling is needed. */
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(columnDefinition = "bytea")
    private byte[] data;

    /** Object key in Cloudflare R2; when set, the image lives there and {@code data} is null. */
    @Column(length = 300)
    private String storageKey;

    private Instant imageUpdatedAt;

    /**
     * 1 (easy, green) to 5 (hard, red) in 0.5 steps. Stored in weight_score because an
     * earlier build created an integer "weight" column that ddl-auto=update cannot retype.
     */
    @Column(name = "weight_score")
    private Double weight;

    /** Expected answering time for teacher analysis; unrelated to the paper's total duration. */
    private Integer timeSeconds;

    /*
     * A question can belong to several competencies ("units" internally), competency levels,
     * contents and learning outcomes. Each list is
     * stored newline-separated (entries are single-line) in its own column; see splitList.
     */
    @Column(name = "units_text", length = 2000)
    private String units;

    @Column(name = "competency_levels_text", length = 2000)
    private String competencyLevels;

    @Column(name = "contents_text", length = 4000)
    private String contents;

    @Column(name = "outcomes_text", length = 4000)
    private String learningOutcomes;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public boolean hasImage() {
        return (storageKey != null && !storageKey.isBlank()) || (data != null && data.length > 0);
    }

    public static final int MAX_LIST_ENTRIES = 10;

    public static List<String> splitList(String stored) {
        if (stored == null || stored.isBlank()) return List.of();
        return java.util.Arrays.stream(stored.split("\n")).map(String::trim).filter(v -> !v.isEmpty()).toList();
    }

    /** Trims entries, flattens line breaks, drops blanks/duplicates, caps entry count and column size. */
    public static String joinList(List<String> values, int entryMax, int columnMax) {
        if (values == null) return null;
        java.util.LinkedHashSet<String> kept = new java.util.LinkedHashSet<>();
        for (String value : values) {
            if (value == null) continue;
            String cleaned = value.replaceAll("\\s+", " ").trim();
            if (cleaned.isEmpty()) continue;
            if (cleaned.length() > entryMax) cleaned = cleaned.substring(0, entryMax);
            kept.add(cleaned);
            if (kept.size() == MAX_LIST_ENTRIES) break;
        }
        StringBuilder joined = new StringBuilder();
        for (String value : kept) {
            if (joined.length() + value.length() + 1 > columnMax) break;
            if (!joined.isEmpty()) joined.append('\n');
            joined.append(value);
        }
        return joined.isEmpty() ? null : joined.toString();
    }

    /** Accepts 1.0-5.0 rounded to the nearest 0.5; anything else means "not set". */
    public static Double normalizeWeight(Double value) {
        if (value == null || value.isNaN() || value < 1 || value > 5) return null;
        return Math.round(value * 2) / 2.0;
    }

    /** "3" or "3.5". */
    public static String weightText(Double weight) {
        if (weight == null) return "";
        return weight % 1 == 0 ? String.valueOf(weight.intValue()) : String.valueOf(weight);
    }

    public static int fullStars(Double weight) {
        return weight == null ? 0 : (int) Math.floor(weight);
    }

    public static boolean halfStar(Double weight) {
        return weight != null && weight % 1 != 0;
    }

    /** Hue for weights 1..5: green, lime, amber, orange, red (mirrored in exam-step.js / exam-question-editor.js). */
    private static final int[] WEIGHT_HUES = {135, 90, 40, 22, 0};

    /** Traffic-light colour; half steps blend the two neighbouring hues. */
    public static String weightColor(Double weight) {
        if (weight == null) return "#94a3b8";
        double position = Math.max(0, Math.min(4, weight - 1));
        int low = (int) Math.floor(position);
        int high = Math.min(4, low + 1);
        long hue = Math.round(WEIGHT_HUES[low] + (WEIGHT_HUES[high] - WEIGHT_HUES[low]) * (position - low));
        return "hsl(" + hue + " 80% 40%)";
    }

    public static String formatTime(Integer seconds) {
        if (seconds == null || seconds <= 0) return "-";
        int minutes = seconds / 60;
        int rest = seconds % 60;
        if (minutes == 0) return rest + " sec";
        return rest == 0 ? minutes + " min" : minutes + " min " + rest + " sec";
    }
}
