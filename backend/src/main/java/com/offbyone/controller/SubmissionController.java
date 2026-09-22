package com.offbyone.controller;

import com.offbyone.judge.JudgeService;
import com.offbyone.model.*;
import com.offbyone.repository.*;
import com.offbyone.sql.SqlProblem;
import com.offbyone.sql.SqlProblemBank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {
    private static final Logger log = LoggerFactory.getLogger(SubmissionController.class);
    private static final int RATE_LIMIT = 5;
    private static final Duration RATE_WINDOW = Duration.ofSeconds(10);

    private final SubmissionRepository submissionRepo;
    private final ProblemRepository problemRepo;
    private final SqlProblemBank sqlProblemBank;
    private final RoomRepository roomRepo;
    private final RoomProblemRepository roomProblemRepo;
    private final JudgeService judgeService;
    private final StringRedisTemplate redis;

    public SubmissionController(SubmissionRepository s, ProblemRepository p, SqlProblemBank sqlProblemBank, RoomRepository r,
                                 RoomProblemRepository rp, JudgeService j, StringRedisTemplate redis) {
        this.submissionRepo = s; this.problemRepo = p; this.sqlProblemBank = sqlProblemBank; this.roomRepo = r;
        this.roomProblemRepo = rp; this.judgeService = j; this.redis = redis;
    }

    @PostMapping
    public ResponseEntity<?> submit(@RequestBody Map<String, String> body, @AuthenticationPrincipal User user) {
        if (rateLimited(user)) return ResponseEntity.status(429).body("Too many submissions, slow down");

        boolean isSql = "sql".equals(body.get("language"));
        Room room = null;
        if (body.containsKey("roomId")) {
            room = roomRepo.findById(UUID.fromString(body.get("roomId"))).orElse(null);
        }

        if (isSql) {
            SqlProblem sqlProblem = sqlProblemBank.findBySlug(body.get("slug")).orElse(null);
            if (sqlProblem == null) return ResponseEntity.badRequest().body("Problem not found");
            if (room != null && !inScope(room.getId(), null, sqlProblem.slug()))
                return ResponseEntity.badRequest().body("Problem not assigned to this room");

            Submission sub = new Submission();
            sub.setUser(user); sub.setSqlSlug(sqlProblem.slug()); sub.setRoom(room);
            sub.setLanguage("sql"); sub.setCode(body.get("code"));
            sub = submissionRepo.save(sub);
            judgeService.judgeSql(sub, sqlProblem);
            return ResponseEntity.ok(Map.of("submissionId", sub.getId(), "status", "queued"));
        }

        Problem problem = problemRepo.findBySlug(body.get("slug")).orElse(null);
        if (problem == null) return ResponseEntity.badRequest().body("Problem not found");
        if (room != null && !inScope(room.getId(), problem.getId(), null))
            return ResponseEntity.badRequest().body("Problem not assigned to this room");

        Submission sub = new Submission();
        sub.setUser(user); sub.setProblem(problem); sub.setRoom(room);
        sub.setLanguage(body.get("language")); sub.setCode(body.get("code"));
        sub = submissionRepo.save(sub);
        judgeService.judge(sub);
        return ResponseEntity.ok(Map.of("submissionId", sub.getId(), "status", "queued"));
    }

    private boolean inScope(UUID roomId, UUID problemId, String sqlSlug) {
        List<RoomProblem> assigned = roomProblemRepo.findByRoomIdOrderBySortOrderAsc(roomId);
        if (assigned.isEmpty()) return true;
        return assigned.stream().anyMatch(rp -> sqlSlug != null
                ? sqlSlug.equals(rp.getSqlSlug())
                : rp.getProblem() != null && rp.getProblem().getId().equals(problemId));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> get(@PathVariable UUID id) {
        return submissionRepo.findById(id).map(s -> ResponseEntity.ok(toDto(s))).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/my")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine(@AuthenticationPrincipal User user) {
        return submissionRepo.findByUserId(user.getId()).stream().map(this::toDto).toList();
    }

    private Map<String, Object> toDto(Submission s) {
        Map<String, Object> dto = new java.util.HashMap<>();
        dto.put("id", s.getId());
        dto.put("problemSlug", s.getProblem() != null ? s.getProblem().getSlug() : s.getSqlSlug());
        dto.put("problemTitle", s.getProblem() != null ? s.getProblem().getTitle() : s.getSqlSlug());
        dto.put("language", s.getLanguage());
        dto.put("verdict", s.getVerdict());
        dto.put("runtimeMs", s.getRuntimeMs());
        dto.put("submittedAt", s.getSubmittedAt());
        return dto;
    }

    private boolean rateLimited(User user) {
        String key = "ratelimit:submit:" + user.getId();
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) redis.expire(key, RATE_WINDOW);
            return count != null && count > RATE_LIMIT;
        } catch (RuntimeException e) {
            log.warn("rate limiter unavailable, allowing submission: {}", e.getMessage());
            return false;
        }
    }
}
