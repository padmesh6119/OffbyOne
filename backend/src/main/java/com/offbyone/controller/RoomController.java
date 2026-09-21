package com.offbyone.controller;

import com.offbyone.model.*;
import com.offbyone.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomRepository roomRepo;
    private final SubmissionRepository submissionRepo;

    public RoomController(RoomRepository roomRepo, SubmissionRepository submissionRepo) {
        this.roomRepo = roomRepo; this.submissionRepo = submissionRepo;
    }

    @PostMapping
    public ResponseEntity<Room> create(@RequestBody Map<String, String> body, @AuthenticationPrincipal User user) {
        Room room = new Room();
        room.setName(body.get("name")); room.setHost(user); room.setJoinCode(generateCode());
        return ResponseEntity.ok(roomRepo.save(room));
    }

    @PostMapping("/{code}/join")
    public ResponseEntity<?> join(@PathVariable String code, @AuthenticationPrincipal User user) {
        return roomRepo.findByJoinCode(code)
                .map(r -> ResponseEntity.<Object>ok(Map.of("roomId", r.getId(), "name", r.getName(), "status", r.getStatus())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<?> start(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return roomRepo.findById(id).map(r -> {
            if (!r.getHost().getId().equals(user.getId())) return ResponseEntity.status(403).<Object>build();
            r.setStatus("active"); r.setStartTime(java.time.LocalDateTime.now());
            return ResponseEntity.<Object>ok(roomRepo.save(r));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/leaderboard")
    public ResponseEntity<?> leaderboard(@PathVariable UUID id) {
        List<Submission> subs = submissionRepo.findByRoomId(id);
        Map<UUID, Map<String, Object>> scores = new HashMap<>();
        for (Submission s : subs) {
            if (!"accepted".equals(s.getVerdict())) continue;
            UUID uid = s.getUser().getId();
            scores.computeIfAbsent(uid, k -> new HashMap<>());
            scores.get(uid).putIfAbsent("username", s.getUser().getUsername());
            scores.get(uid).merge("score", 100, (a, b) -> (int) a + (int) b);
        }
        return ResponseEntity.ok(scores.values());
    }

    private String generateCode() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
