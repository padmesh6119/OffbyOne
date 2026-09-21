package com.offbyone.controller;

import com.offbyone.judge.JudgeService;
import com.offbyone.model.*;
import com.offbyone.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {
    private final SubmissionRepository submissionRepo;
    private final ProblemRepository problemRepo;
    private final RoomRepository roomRepo;
    private final JudgeService judgeService;

    @PostMapping
    public ResponseEntity<?> submit(@RequestBody Map<String, String> body,
                                    @AuthenticationPrincipal User user) {
        Problem problem = problemRepo.findBySlug(body.get("slug"))
                .orElse(null);
        if (problem == null) return ResponseEntity.badRequest().body("Problem not found");

        Submission sub = new Submission();
        sub.setUser(user);
        sub.setProblem(problem);
        sub.setLanguage(body.get("language"));
        sub.setCode(body.get("code"));

        if (body.containsKey("roomId")) {
            roomRepo.findById(UUID.fromString(body.get("roomId")))
                    .ifPresent(sub::setRoom);
        }

        sub = submissionRepo.save(sub);
        judgeService.judge(sub);

        return ResponseEntity.ok(Map.of("submissionId", sub.getId(), "status", "queued"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Submission> get(@PathVariable UUID id) {
        return submissionRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/my")
    public List<Submission> mine(@AuthenticationPrincipal User user) {
        return submissionRepo.findByUserId(user.getId());
    }
}
