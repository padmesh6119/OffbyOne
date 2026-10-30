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

    private static final java.util.concurrent.Semaphore CAPACITY = new java.util.concurrent.Semaphore(4);

    public Verdict runSample(SqlProblem problem, String query) { return evaluate(problem, query, false); }
    public Verdict judge(SqlProblem problem, String query) { return evaluate(problem, query, true); }

    private Verdict evaluate(SqlProblem problem, String query, boolean hidden) {
        String body;
        try { body = validate(query); }
        catch (IllegalArgumentException e) { return Verdict.error("error", e.getMessage()); }
        if (!CAPACITY.tryAcquire()) return Verdict.error("busy", "Judge is busy. Try again shortly.");
        try {
            Table actual = execute(problem, problem.seedData(), body);
            Table expected = execute(problem, problem.seedData(), problem.referenceQuery());
            if (!compare(expected, actual, problem.ordered()))
                return new Verdict("wrong_answer", "The sample result does not match.", actual.columns(), actual.rows(), expected.columns(), expected.rows());
            if (hidden) {
                if (problem.hiddenDatasets() == null || problem.hiddenDatasets().isEmpty())
                    return Verdict.error("error", "This challenge is not ready for judging.");
                for (List<String> seed : problem.hiddenDatasets()) {
                    Table hiddenActual = execute(problem, seed, body);
                    Table hiddenExpected = execute(problem, seed, problem.referenceQuery());
                    if (!compare(hiddenExpected, hiddenActual, problem.ordered()))
                        return new Verdict("wrong_answer", "Sample passed. A hidden dataset failed. Check duplicates, missing rows, ties and ordering.", actual.columns(), actual.rows(), null, null);
                }
            }
            return new Verdict(hidden ? "accepted" : "sample_passed", hidden ? "All sample and hidden datasets passed." : "Sample passed. Submit to check hidden datasets.", actual.columns(), actual.rows(), null, null);
        } catch (SQLException e) {
            String msg = e.getMessage() == null ? "Query could not be executed." : e.getMessage();
            return Verdict.error(msg.contains("SQLITE_INTERRUPT") ? "tle" : "error",
                msg.contains("SQLITE_INTERRUPT") ? "Query exceeded its execution budget." : msg);
        } finally { CAPACITY.release(); }
    }

    // Strip comments and quoted literals before checking statement boundaries/keywords.
    // query_only and disabled extensions provide an independent database-level boundary.
    static String validate(String query) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("Write a query first.");
        if (query.length() > 16000) throw new IllegalArgumentException("Query is too long (16,000 characters maximum).");
        StringBuilder tokens = new StringBuilder();
        for (int i=0; i<query.length();) {
            char c=query.charAt(i);
            if (c=='\'' || c=='"' || c=='`' || c=='[') {
                char end=c=='[' ? ']' : c; i++; boolean closed=false;
                while(i<query.length()) {
                    if(query.charAt(i++)==end) {
                        if(i<query.length() && query.charAt(i)==end) { i++; continue; }
                        closed=true; break;
                    }
                }
                if(!closed) throw new IllegalArgumentException("Unclosed quoted value.");
                tokens.append(" value ");
            } else if(c=='-' && i+1<query.length() && query.charAt(i+1)=='-') {
                while(i<query.length() && query.charAt(i)!='\n') i++; tokens.append(' ');
            } else if(c=='/' && i+1<query.length() && query.charAt(i+1)=='*') {
                int end=query.indexOf("*/",i+2);
                if(end<0) throw new IllegalArgumentException("Unclosed comment.");
                i=end+2; tokens.append(' ');
            } else { tokens.append(c); i++; }
        }
        String clean=tokens.toString().trim().toUpperCase(Locale.ROOT);
        if(clean.endsWith(";")) clean=clean.substring(0,clean.length()-1);
        if(clean.contains(";") || !clean.matches("(?s)^(SELECT|WITH)\\b.*"))
            throw new IllegalArgumentException("Use one SELECT or WITH statement.");
        for(String keyword: FORBIDDEN_KEYWORDS)
            if(clean.matches("(?s).*\\b"+keyword+"\\b.*"))
                throw new IllegalArgumentException("Only read-only queries are allowed.");
        return query;
    }

    record Table(List<String> columns, List<List<Object>> rows) {}

    private Table execute(SqlProblem problem, List<String> seed, String query) throws SQLException {
        try(Connection con=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try(Statement st=con.createStatement()) {
                for(String sql:problem.schema()) st.execute(sql);
                for(String sql:seed) st.execute(sql);
                st.execute("PRAGMA query_only=ON");
                st.execute("PRAGMA temp_store=MEMORY");
            }
            var sqlite=(org.sqlite.SQLiteConnection)con;
            sqlite.setLimit(org.sqlite.SQLiteLimits.SQLITE_LIMIT_LENGTH, 65536);
            sqlite.setLimit(org.sqlite.SQLiteLimits.SQLITE_LIMIT_COLUMN, 64);
            sqlite.setLimit(org.sqlite.SQLiteLimits.SQLITE_LIMIT_VDBE_OP, 100000);
            long deadline=System.nanoTime()+2_000_000_000L;
            org.sqlite.ProgressHandler.setHandler(con, 1000, new org.sqlite.ProgressHandler() {
                int calls=0;
                protected int progress() { return ++calls>10000 || System.nanoTime()>deadline ? 1 : 0; }
            });
            try(Statement st=con.createStatement(); ResultSet rs=st.executeQuery(query)) {
                int count=rs.getMetaData().getColumnCount();
                List<String> columns=new ArrayList<>(); List<List<Object>> rows=new ArrayList<>();
                for(int i=1;i<=count;i++) columns.add(rs.getMetaData().getColumnLabel(i));
                int bytes=0;
                while(rs.next()) {
                    if(rows.size()>=ROW_CAP) throw new SQLException("Result exceeds 1,000 rows.");
                    List<Object> row=new ArrayList<>();
                    for(int i=1;i<=count;i++) {
                        Object value=rs.getObject(i);
                        if(value instanceof byte[]) throw new SQLException("Return text or numbers, not binary values.");
                        bytes += value==null ? 4 : value.toString().length();
                        if(bytes>262144) throw new SQLException("Result is too large.");
                        row.add(value);
                    }
                    rows.add(row);
                }
                return new Table(columns,rows);
            }
        }
    }

    static boolean compare(Table expected, Table actual, boolean ordered) {
        if(expected.columns().size()!=actual.columns().size() || expected.rows().size()!=actual.rows().size()) return false;
        List<List<Object>> exp=expected.rows().stream().map(SqlJudge::normalizeRow).toList();
        List<List<Object>> act=actual.rows().stream().map(SqlJudge::normalizeRow).toList();
        if(ordered) return exp.equals(act);
        Map<List<Object>,Integer> counts=new HashMap<>();
        for(var row:exp) counts.merge(row,1,Integer::sum);
        for(var row:act) counts.merge(row,-1,Integer::sum);
        return counts.values().stream().allMatch(n->n==0);
    }

    private static List<Object> normalizeRow(List<Object> row) {
        return row.stream().map(v -> v instanceof Number ? new java.math.BigDecimal(v.toString()).stripTrailingZeros() : v).toList();
    }
}
