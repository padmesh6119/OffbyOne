package com.offbyone.controller;

import com.offbyone.model.Problem;
import com.offbyone.model.TestCase;
import com.offbyone.repository.ProblemRepository;
import com.offbyone.repository.TestCaseRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {
    private final ProblemRepository problemRepo;
    private final TestCaseRepository testCaseRepo;

    public ProblemController(ProblemRepository problemRepo, TestCaseRepository testCaseRepo) {
        this.problemRepo = problemRepo; this.testCaseRepo = testCaseRepo;
    }

    @GetMapping
    public List<Problem> list(@RequestParam(required = false) String difficulty) {
        return difficulty != null ? problemRepo.findByDifficultyAndIsActiveTrue(difficulty) : problemRepo.findByIsActiveTrue();
    }

    @GetMapping("/{slug}")
    public ResponseEntity<Problem> get(@PathVariable String slug) {
        return problemRepo.findBySlug(slug).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{slug}/samples")
    public ResponseEntity<?> samples(@PathVariable String slug) {
        return problemRepo.findBySlug(slug)
                .map(p -> ResponseEntity.ok(testCaseRepo.findByProblemIdAndIsSampleTrue(p.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Problem create(@RequestBody Problem problem) { return problemRepo.save(problem); }

    @PostMapping("/{id}/testcases")
    public TestCase addTestCase(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Problem problem = problemRepo.findById(id).orElseThrow();
        TestCase tc = new TestCase();
        tc.setProblem(problem);
        tc.setInput((String) body.get("input"));
        tc.setExpectedOutput((String) body.get("expected_output"));
        tc.setSample((Boolean) body.getOrDefault("is_sample", false));
        return testCaseRepo.save(tc);
    }
}
