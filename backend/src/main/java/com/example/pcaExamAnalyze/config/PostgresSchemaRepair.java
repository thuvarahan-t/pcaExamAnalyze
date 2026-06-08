package com.example.pcaExamAnalyze.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.stream.Collectors;

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
        repairLegacyQuestionColumns();
        repairLegacyAttemptColumns();
        repairReferenceTypeConstraint();
    }

    private boolean isPostgres() throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return c.getMetaData().getURL().startsWith("jdbc:postgresql:");
        }
    }

    private void repairLegacyQuestionColumns() {
        Set<String> questionColumns = jdbc.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = current_schema()
                  and table_name = 'questions'
                """, String.class).stream().collect(Collectors.toSet());

        if (questionColumns.contains("section")) {
            // Old builds stored the section name as questions.section. Move those values into
            // the normalized sections table when possible, then make the legacy column harmless.
            jdbc.execute("""
                    insert into sections(name)
                    select distinct trim(section)
                    from questions
                    where section_id is null
                      and section is not null
                      and trim(section) <> ''
                    on conflict (name) do nothing
                    """);
            jdbc.execute("""
                    update questions q
                    set section_id = s.id
                    from sections s
                    where q.section_id is null
                      and q.section is not null
                      and lower(s.name) = lower(trim(q.section))
                    """);
            jdbc.execute("alter table questions alter column section drop not null");
        }

        if (questionColumns.contains("topic")) {
            jdbc.execute("alter table questions alter column topic drop not null");
        }
    }

    private void repairReferenceTypeConstraint() {
        Integer hasReferences = jdbc.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = current_schema()
                  and table_name = 'question_references'
                """, Integer.class);
        if (hasReferences == null || hasReferences == 0) {
            return;
        }

        jdbc.execute("alter table question_references drop constraint if exists question_references_type_check");
        jdbc.execute("""
                alter table question_references
                add constraint question_references_type_check
                check (type in ('FILE', 'VIDEO', 'PRACTICAL'))
                """);
    }

    private void repairLegacyAttemptColumns() {
        Set<String> attemptColumns = jdbc.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = current_schema()
                  and table_name = 'attempts'
                """, String.class).stream().collect(Collectors.toSet());

        if (attemptColumns.contains("paper_structure_id")) {
            // Older builds stored one attempt per paper. Current attempts are cross-paper sheets,
            // so this legacy column must not block inserts.
            jdbc.execute("alter table attempts alter column paper_structure_id drop not null");
        }
    }
}
