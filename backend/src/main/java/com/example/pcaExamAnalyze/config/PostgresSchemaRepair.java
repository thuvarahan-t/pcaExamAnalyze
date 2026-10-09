package com.example.pcaExamAnalyze.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Repairs legacy Supabase schema details that Hibernate's ddl-auto=update cannot remove.
 */
@Component
public class PostgresSchemaRepair implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public PostgresSchemaRepair(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!isPostgres()) {
            return;
        }
        ensureAnswerUpsertIndex();
    }

    /** McqAnswerWriter upserts on (submission_id, question_number); make sure that key is unique. */
    private void ensureAnswerUpsertIndex() {
        try {
            jdbc.execute("create unique index if not exists uq_mcq_answers_submission_question "
                    + "on mcq_submission_answers (submission_id, question_number)");
        } catch (RuntimeException ex) {
            System.err.println("Could not create unique index on mcq_submission_answers: " + ex.getMessage());
        }
        try {
            jdbc.execute("create unique index if not exists uq_mcq_times_submission_question "
                    + "on mcq_submission_question_times (submission_id, question_number)");
        } catch (RuntimeException ex) {
            System.err.println("Could not create unique index on mcq_submission_question_times: " + ex.getMessage());
        }
    }

    private boolean isPostgres() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return c.getMetaData().getURL().startsWith("jdbc:postgresql:");
        }
    }
}
