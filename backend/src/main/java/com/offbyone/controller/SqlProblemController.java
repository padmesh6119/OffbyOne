package com.offbyone.controller;

import com.offbyone.sql.SqlJudge;
import com.offbyone.sql.SqlProblem;
import com.offbyone.sql.SqlProblemBank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sql-problems")
public class SqlProblemController {
    private final SqlProblemBank bank;
    private final SqlJudge judge;

    public SqlProblemController(SqlProblemBank bank, SqlJudge judge) {
        this.bank = bank; this.judge = judge;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return bank.findAll().stream()
                .map(p -> Map.<String, Object>of("slug", p.slug(), "title", p.title(),
                        "difficulty", p.difficulty(), "rating", p.rating(), "pattern", p.pattern()))
                .toList();
    }

    @GetMapping("/{slug}")
    public ResponseEntity<?> get(@PathVariable String slug) {
        return bank.findBySlug(slug)
                .map(p -> ResponseEntity.ok(Map.of("slug", p.slug(), "title", p.title(),
                        "difficulty", p.difficulty(), "rating", p.rating(), "pattern", p.pattern(),
                        "schema", p.schema(), "task", p.task(), "tables", judge.previewTables(p))))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{slug}/submit")
    public ResponseEntity<?> submit(@PathVariable String slug, @RequestBody Map<String, String> body) {
        return bank.findBySlug(slug).<ResponseEntity<?>>map(p -> {
            SqlJudge.Verdict v = judge.judge(p, body.get("query"));
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("verdict", v.status());
            result.put("message", v.message());
            if (v.actualColumns() != null) result.put("actual", Map.of("columns", v.actualColumns(), "rows", v.actualRows()));
            if (v.expectedColumns() != null) result.put("expected", Map.of("columns", v.expectedColumns(), "rows", v.expectedRows()));
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.notFound().build());
    }
}
