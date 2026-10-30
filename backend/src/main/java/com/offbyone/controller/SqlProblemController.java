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
    private final com.offbyone.repository.SubmissionRepository submissions;
    private final com.offbyone.arena.SqlRequestLimit limit;

    public SqlProblemController(SqlProblemBank bank, SqlJudge judge, com.offbyone.repository.SubmissionRepository submissions, com.offbyone.arena.SqlRequestLimit limit) {
        this.bank = bank; this.judge = judge; this.submissions=submissions; this.limit=limit;
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

    @PostMapping("/{slug}/run")
    public ResponseEntity<?> run(@PathVariable String slug, @RequestBody Map<String,String> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.offbyone.model.User user) {
        limit.check(user);
        return bank.findBySlug(slug).<ResponseEntity<?>>map(p -> ResponseEntity.ok(judge.runSample(p,body.get("query"))))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{slug}/submit")
    public ResponseEntity<?> submit(@PathVariable String slug, @RequestBody Map<String, String> body, @org.springframework.security.core.annotation.AuthenticationPrincipal com.offbyone.model.User user) {
        limit.check(user);
        String query=body.get("query");
        if (query==null || query.isBlank() || query.length()>16000) return ResponseEntity.badRequest().body("Write a query of 1–16,000 characters.");
        return bank.findBySlug(slug).<ResponseEntity<?>>map(p -> {
            SqlJudge.Verdict v = judge.judge(p, query);
            if (!"busy".equals(v.status())) {
                var submission=new com.offbyone.model.Submission(); submission.setUser(user); submission.setSqlSlug(slug);
                submission.setLanguage("sql"); submission.setCode(query); submission.setVerdict(v.status()); submissions.save(submission);
            }
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("verdict", v.status());
            result.put("message", v.message());
            if (v.actualColumns() != null) result.put("actual", Map.of("columns", v.actualColumns(), "rows", v.actualRows()));
            if (v.expectedColumns() != null) result.put("expected", Map.of("columns", v.expectedColumns(), "rows", v.expectedRows()));
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.notFound().build());
    }
}
