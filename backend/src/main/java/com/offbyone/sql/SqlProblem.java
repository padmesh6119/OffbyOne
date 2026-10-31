package com.offbyone.sql;

import java.util.List;

public record SqlProblem(
        String slug,
        String title,
        String difficulty,
        int rating,
        String pattern,
        List<String> schema,
        List<String> seedData,
        String task,
        String referenceQuery,
        boolean ordered,
        Expected expected,
        List<List<String>> hiddenDatasets,
        String explanation,
        String hint
) {
    public record Expected(List<String> columns, List<List<Object>> rows) {}
}
