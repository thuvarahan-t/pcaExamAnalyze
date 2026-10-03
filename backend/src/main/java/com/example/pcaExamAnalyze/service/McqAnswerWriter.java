package com.example.pcaExamAnalyze.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;

/**
 * Saves a single answer with one SQL statement. The autosave path used to load the whole
 * submission (exam + every answer) and rewrite it, holding one of the few pooled connections
 * for hundreds of milliseconds; this keeps the hold to a few milliseconds.
 *
 * <p>The statement itself checks that the paper is still in progress, inside its time (plus
 * grace) and that the question/option are in range, so no extra round trips are needed.
 */
@Service
public class McqAnswerWriter {

    /** Answers sent this many seconds after the deadline are still accepted (network delay at 00:00). */
    static final long ANSWER_GRACE_SECONDS = 20;

    private static final String POSTGRES_UPSERT = """
            INSERT INTO mcq_submission_answers (submission_id, question_number, selected_option)
            SELECT s.id, ?::int, ?::int
            FROM mcq_submissions s JOIN mcq_exams e ON e.id = s.exam_id
            WHERE s.id = ?
              AND s.status = 'IN_PROGRESS'
              AND ?::int BETWEEN 1 AND e.total_questions
              AND ?::int BETWEEN 1 AND e.options_per_question
              AND (e.duration_minutes IS NULL OR e.duration_minutes <= 0 OR s.started_at IS NULL
                   OR s.started_at + ((e.duration_minutes * 60 + ?) * INTERVAL '1 second') > now())
            ON CONFLICT (submission_id, question_number) DO UPDATE SET selected_option = EXCLUDED.selected_option
            """;

    private static final String POSTGRES_CLEAR = """
            DELETE FROM mcq_submission_answers a
            WHERE a.submission_id = ? AND a.question_number = ?
              AND EXISTS (SELECT 1 FROM mcq_submissions s JOIN mcq_exams e ON e.id = s.exam_id
                          WHERE s.id = a.submission_id
                            AND s.status = 'IN_PROGRESS'
                            AND (e.duration_minutes IS NULL OR e.duration_minutes <= 0 OR s.started_at IS NULL
                                 OR s.started_at + ((e.duration_minutes * 60 + ?) * INTERVAL '1 second') > now()))
            """;

    /** Time per question only ever grows (GREATEST), so a late or repeated request cannot lower it. */
    private static final String POSTGRES_TIME_UPSERT = """
            INSERT INTO mcq_submission_question_times (submission_id, question_number, seconds_spent)
            SELECT s.id, ?::int, ?::int
            FROM mcq_submissions s JOIN mcq_exams e ON e.id = s.exam_id
            WHERE s.id = ?
              AND s.status = 'IN_PROGRESS'
              AND ?::int BETWEEN 1 AND e.total_questions
            ON CONFLICT (submission_id, question_number) DO UPDATE
              SET seconds_spent = GREATEST(mcq_submission_question_times.seconds_spent, EXCLUDED.seconds_spent)
            """;

    private final JdbcTemplate jdbc;
    private final boolean postgres;

    public McqAnswerWriter(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.postgres = detectPostgres(dataSource);
    }

    /** True when the submission exists and belongs to this registration id + NIC. */
    public boolean owns(long submissionId, String registrationId, String nic) {
        if (registrationId == null || nic == null) return false;
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM mcq_submissions WHERE id = ? AND upper(registration_id) = upper(?) AND nic = ?",
                Integer.class, submissionId, registrationId, nic);
        return count != null && count > 0;
    }

    /** Stores one answer. Returns false when the paper no longer accepts it (submitted, time over, invalid). */
    public boolean save(long submissionId, int question, int option) {
        if (postgres) {
            return jdbc.update(POSTGRES_UPSERT, question, option, submissionId, question, option,
                    ANSWER_GRACE_SECONDS) > 0;
        }
        return saveFallback(submissionId, question, option);
    }

    /** Stores time spent per question (seconds, cumulative). Best effort: values are bounded and only grow. */
    public void saveTimes(long submissionId, Map<Integer, Integer> seconds) {
        if (seconds == null || seconds.isEmpty()) return;
        var rows = new java.util.ArrayList<Object[]>();
        seconds.forEach((question, value) -> {
            if (question == null || value == null || question < 1) return;
            rows.add(new Object[]{question, Math.max(0, Math.min(value, 6 * 60 * 60)), submissionId, question});
        });
        if (rows.isEmpty()) return;
        if (postgres) {
            jdbc.batchUpdate(POSTGRES_TIME_UPSERT, rows);
            return;
        }
        for (Object[] row : rows) {
            int question = (Integer) row[0];
            int value = (Integer) row[1];
            if (acceptingFallback(submissionId, question, 1) == null) continue;
            int updated = jdbc.update("UPDATE mcq_submission_question_times SET seconds_spent = ? "
                    + "WHERE submission_id = ? AND question_number = ? AND seconds_spent < ?",
                    value, submissionId, question, value);
            Integer exists = jdbc.queryForObject("SELECT count(*) FROM mcq_submission_question_times "
                    + "WHERE submission_id = ? AND question_number = ?", Integer.class, submissionId, question);
            if (updated == 0 && (exists == null || exists == 0)) {
                jdbc.update("INSERT INTO mcq_submission_question_times (submission_id, question_number, seconds_spent) "
                        + "VALUES (?, ?, ?)", submissionId, question, value);
            }
        }
    }

    /** Removes an answer (the student cleared it). Idempotent; ignored once the paper is closed. */
    public void clear(long submissionId, int question) {
        if (postgres) {
            jdbc.update(POSTGRES_CLEAR, submissionId, question, ANSWER_GRACE_SECONDS);
            return;
        }
        if (acceptingFallback(submissionId, question, 1) != null) {
            jdbc.update("DELETE FROM mcq_submission_answers WHERE submission_id = ? AND question_number = ?",
                    submissionId, question);
        }
    }

    /** Portable path for the H2 dev/test database, which has no ON CONFLICT upsert. */
    private boolean saveFallback(long submissionId, int question, int option) {
        if (acceptingFallback(submissionId, question, option) == null) return false;
        int updated = jdbc.update("UPDATE mcq_submission_answers SET selected_option = ? "
                + "WHERE submission_id = ? AND question_number = ?", option, submissionId, question);
        if (updated == 0) {
            jdbc.update("INSERT INTO mcq_submission_answers (submission_id, question_number, selected_option) "
                    + "VALUES (?, ?, ?)", submissionId, question, option);
        }
        return true;
    }

    /** Returns a non-null marker when the paper accepts this question/option right now. */
    private Boolean acceptingFallback(long submissionId, int question, int option) {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("""
                    SELECT s.status AS status, s.started_at AS started_at, e.duration_minutes AS minutes,
                           e.total_questions AS total, e.options_per_question AS options
                    FROM mcq_submissions s JOIN mcq_exams e ON e.id = s.exam_id WHERE s.id = ?
                    """, submissionId);
        } catch (org.springframework.dao.EmptyResultDataAccessException missing) {
            return null;
        }
        if (!"IN_PROGRESS".equals(row.get("status"))) return null;
        if (question < 1 || question > ((Number) row.get("total")).intValue()) return null;
        if (option < 1 || option > ((Number) row.get("options")).intValue()) return null;
        Object minutes = row.get("minutes");
        Object started = row.get("started_at");
        if (minutes != null && ((Number) minutes).intValue() > 0 && started != null) {
            Instant startedAt = started instanceof java.sql.Timestamp ts ? ts.toInstant()
                    : ((java.time.OffsetDateTime) started).toInstant();
            Instant limit = startedAt.plusSeconds(((Number) minutes).longValue() * 60 + ANSWER_GRACE_SECONDS);
            if (!Instant.now().isBefore(limit)) return null;
        }
        return Boolean.TRUE;
    }

    private static boolean detectPostgres(DataSource dataSource) {
        try (Connection c = dataSource.getConnection()) {
            return c.getMetaData().getURL().startsWith("jdbc:postgresql:");
        } catch (SQLException ex) {
            return false;
        }
    }
}
