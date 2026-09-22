package com.offbyone.sql;

import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

/**
 * Executes a player's SQL against a fresh in-memory SQLite database built from the problem's
 * own schema + seed data. Never touches the real Postgres instance — SQLite is a separate JDBC
 * driver entirely, so a query like "SELECT * FROM users" simply fails with "no such table".
 */
@Service
public class SqlJudge {
    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE", "ATTACH", "DETACH",
            "REPLACE", "TRUNCATE", "VACUUM", "REINDEX", "TRIGGER", "PRAGMA");
    private static final int QUERY_TIMEOUT_SECONDS = 2;
    private static final int ROW_CAP = 1000;

    public record Verdict(String status, String message,
                           List<String> actualColumns, List<List<Object>> actualRows,
                           List<String> expectedColumns, List<List<Object>> expectedRows) {
        static Verdict error(String status, String message) {
            return new Verdict(status, message, null, null, null, null);
        }
    }

    /** Schema + ~5 sample rows per table, for the frontend's schema panel. Never exposes referenceQuery/expected. */
    public List<Map<String, Object>> previewTables(SqlProblem problem) {
        List<Map<String, Object>> tables = new ArrayList<>();
        try (Connection con = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement setup = con.createStatement()) {
                for (String stmt : problem.schema()) setup.execute(stmt);
                for (String stmt : problem.seedData()) setup.execute(stmt);
            }
            for (String createStmt : problem.schema()) {
                String tableName = extractTableName(createStmt);
                if (tableName == null) continue;
                List<String> columns = new ArrayList<>();
                List<List<Object>> rows = new ArrayList<>();
                try (Statement st = con.createStatement();
                     ResultSet rs = st.executeQuery("SELECT * FROM " + tableName + " LIMIT 5")) {
                    int colCount = rs.getMetaData().getColumnCount();
                    for (int i = 1; i <= colCount; i++) columns.add(rs.getMetaData().getColumnName(i));
                    while (rs.next()) {
                        List<Object> row = new ArrayList<>();
                        for (int i = 1; i <= colCount; i++) row.add(rs.getObject(i));
                        rows.add(row);
                    }
                }
                tables.add(Map.of("name", tableName, "columns", columns, "rows", rows));
            }
        } catch (SQLException e) {
            // problem schema is author-provided/trusted; on unexpected failure just return no preview
        }
        return tables;
    }

    private String extractTableName(String createStmt) {
        var m = java.util.regex.Pattern.compile(
                "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[\"'`]?(\\w+)[\"'`]?").matcher(createStmt);
        return m.find() ? m.group(1) : null;
    }

    public Verdict judge(SqlProblem problem, String playerQuery) {
        String trimmed = playerQuery == null ? "" : playerQuery.trim();
        if (trimmed.isEmpty()) return Verdict.error("error", "Empty query");

        String body = trimmed.endsWith(";") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        if (body.contains(";")) return Verdict.error("error", "Only a single statement is allowed");

        String upper = body.toUpperCase(Locale.ROOT);
        if (!(upper.startsWith("SELECT") || upper.startsWith("WITH"))) {
            return Verdict.error("error", "Only SELECT queries are allowed");
        }
        for (String kw : FORBIDDEN_KEYWORDS) {
            if (upper.matches("(?s).*\\b" + kw + "\\b.*")) {
                return Verdict.error("error", "Query contains a disallowed keyword: " + kw);
            }
        }

        try (Connection con = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement setup = con.createStatement()) {
                for (String stmt : problem.schema()) setup.execute(stmt);
                for (String stmt : problem.seedData()) setup.execute(stmt);
            }

            List<String> actualColumns = new ArrayList<>();
            List<List<Object>> actualRows = new ArrayList<>();
            try (Statement st = con.createStatement()) {
                st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                st.setMaxRows(ROW_CAP);
                try (ResultSet rs = st.executeQuery(body)) {
                    int colCount = rs.getMetaData().getColumnCount();
                    for (int i = 1; i <= colCount; i++) actualColumns.add(rs.getMetaData().getColumnLabel(i));
                    while (rs.next()) {
                        List<Object> row = new ArrayList<>();
                        for (int i = 1; i <= colCount; i++) row.add(rs.getObject(i));
                        actualRows.add(row);
                    }
                }
            }

            boolean match = compare(problem.expected().rows(), actualRows, problem.ordered());
            if (match) return new Verdict("accepted", "", actualColumns, actualRows, null, null);
            return new Verdict("wrong_answer", "", actualColumns, actualRows,
                    problem.expected().columns(), problem.expected().rows());
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase(Locale.ROOT).contains("timeout")) {
                return Verdict.error("tle", "Query timed out");
            }
            return Verdict.error("error", msg == null ? "SQL error" : msg);
        }
    }

    private boolean compare(List<List<Object>> expected, List<List<Object>> actual, boolean ordered) {
        if (expected.size() != actual.size()) return false;
        List<String> expKeys = expected.stream().map(this::rowKey).toList();
        List<String> actKeys = actual.stream().map(this::rowKey).toList();
        if (ordered) return expKeys.equals(actKeys);
        List<String> expSorted = new ArrayList<>(expKeys); Collections.sort(expSorted);
        List<String> actSorted = new ArrayList<>(actKeys); Collections.sort(actSorted);
        return expSorted.equals(actSorted);
    }

    /** Column names are ignored on purpose — comparison is positional, per spec. */
    private String rowKey(List<Object> row) {
        StringBuilder sb = new StringBuilder();
        for (Object v : row) sb.append('|').append(normalize(v));
        return sb.toString();
    }

    /** Numeric values (including numeric-looking strings) compare with a fixed-precision tolerance; NULL is its own bucket. */
    private String normalize(Object v) {
        if (v == null) return "\u0000NULL";
        if (v instanceof Number n) return String.format(Locale.ROOT, "%.6f", n.doubleValue());
        String s = v.toString();
        try {
            return String.format(Locale.ROOT, "%.6f", Double.parseDouble(s));
        } catch (NumberFormatException ignored) {
            return s;
        }
    }
}
