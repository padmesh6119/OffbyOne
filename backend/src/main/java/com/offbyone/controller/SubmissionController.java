package com.offbyone.controller;

import com.offbyone.judge.JudgeService;
import com.offbyone.model.*;
import com.offbyone.repository.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {
    private static final int RATE_LIMIT = 5;
    private static final Duration RATE_WINDOW = Duration.ofSeconds(10);

    private final SubmissionRepository submissionRepo;
    private final ProblemRepository problemRepo;
    private final RoomRepository roomRepo;
    private final RoomProblemRepository roomProblemRepo;
    private final JudgeService judgeService;
    private final StringRedisTemplate redis;

    public SubmissionController(SubmissionRepository s, ProblemRepository p, RoomRepository r,
                                 RoomProblemRepository rp, JudgeService j, StringRedisTemplate redis) {
        this.submissionRepo = s; this.problemRepo = p; this.roomRepo = r;
        this.roomProblemRepo = rp; this.judgeService = j; this.redis = redis;
    }

    @PostMapping
    public ResponseEntity<?> submit(@RequestBody Map<String, String> body, @AuthenticationPrincipal User user) {
        Long count = redis.opsForValue().increment("ratelimit:submit:" + user.getId());
        if (count != null && count == 1) redis.expire("ratelimit:submit:" + user.getId(), RATE_WINDOW);
        if (count != null && count > RATE_LIMIT) return ResponseEntity.status(429).body("Too many submissions, slow down");

        Problem problem = problemRepo.findBySlug(body.get("slug")).orElse(null);
        if (problem == null) return ResponseEntity.badRequest().body("Problem not found");

        Room room = null;
        if (body.containsKey("roomId")) {
            room = roomRepo.findById(UUID.fromString(body.get("roomId"))).orElse(null);
            if (room != null) {
                List<RoomProblem> assigned = roomProblemRepo.findByRoomIdOrderBySortOrderAsc(room.getId());
                boolean inScope = assigned.isEmpty() || assigned.stream().anyMatch(rp -> rp.getProblem().getId().equals(problem.getId()));
                if (!inScope) return ResponseEntity.badRequest().body("Problem not assigned to this room");
            }
        }

        Submission sub = new Submission();
        sub.setUser(user); sub.setProblem(problem); sub.setRoom(room);
        sub.setLanguage(body.get("language")); sub.setCode(body.get("code"));
        sub = submissionRepo.save(sub);
        judgeService.judge(sub);
        return ResponseEntity.ok(Map.of("submissionId", sub.getId(), "status", "queued"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id) {
        return submissionRepo.findById(id).map(s -> ResponseEntity.ok(toDto(s))).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/my")
    public List<Map<String, Object>> mine(@AuthenticationPrincipal User user) {
        return submissionRepo.findByUserId(user.getId()).stream().map(this::toDto).toList();
    }

    private Map<String, Object> toDto(Submission s) {
        Map<String, Object> dto = new java.util.HashMap<>();
        dto.put("id", s.getId());
        dto.put("problemSlug", s.getProblem().getSlug());
        dto.put("problemTitle", s.getProblem().getTitle());
        dto.put("language", s.getLanguage());
        dto.put("verdict", s.getVerdict());
        dto.put("runtimeMs", s.getRuntimeMs());
        dto.put("submittedAt", s.getSubmittedAt());
        return dto;
    }
}
