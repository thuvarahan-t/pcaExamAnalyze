package com.example.pcaExamAnalyze;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records every SQL statement Hibernate prepares, so tests can check what reaches the database. */
public class SqlCapture implements StatementInspector {

    public static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        STATEMENTS.add(sql);
        return sql;
    }
}
