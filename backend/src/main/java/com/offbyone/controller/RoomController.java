package com.offbyone.controller;

import com.offbyone.model.*;
import com.offbyone.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomRepository roomRepo;
    private final RoomParticipantRepository participantRepo;
    private final RoomProblemRepository roomProblemRepo;
    private final ProblemRepository problemRepo;
    private final SimpMessagingTemplate ws;

    public RoomController(RoomRepository roomRepo, RoomParticipantRepository participantRepo,
                           RoomProblemRepository roomProblemRepo, ProblemRepository problemRepo,
                           SimpMessagingTemplate ws) {
        this.roomRepo = roomRepo; this.participantRepo = participantRepo;
        this.roomProblemRepo = roomProblemRepo; this.problemRepo = problemRepo; this.ws = ws;
    }

    @PostMapping
    public ResponseEntity<Room> create(@RequestBody Map<String, String> body, @AuthenticationPrincipal User user) {
        Room room = new Room();
        room.setName(body.get("name")); room.setHost(user); room.setJoinCode(generateCode());
        room = roomRepo.save(room);
        addParticipant(room, user);
        return ResponseEntity.ok(room);
    }

    @PostMapping("/{code}/join")
    public ResponseEntity<?> join(@PathVariable String code, @AuthenticationPrincipal User user) {
        return roomRepo.findByJoinCode(code).map(r -> {
            addParticipant(r, user);
            ws.convertAndSend("/topic/room/" + r.getId() + "/lobby", Map.of("event", "joined", "username", user.getUsername()));
            return ResponseEntity.<Object>ok(Map.of("roomId", r.getId(), "name", r.getName(), "joinCode", r.getJoinCode(), "status", r.getStatus()));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/participants")
    public List<Map<String, Object>> participants(@PathVariable UUID id) {
        return participantRepo.findByRoomIdOrderByScoreDesc(id).stream()
                .map(p -> Map.<String, Object>of("userId", p.getUser().getId(), "username", p.getUser().getUsername(), "score", p.getScore()))
                .toList();
    }

    @PostMapping("/{id}/problems")
    public ResponseEntity<?> assignProblems(@PathVariable UUID id, @RequestBody List<Map<String, Object>> body, @AuthenticationPrincipal User user) {
        return roomRepo.findById(id).map(room -> {
            if (!room.getHost().getId().equals(user.getId())) return ResponseEntity.status(403).<Object>build();
            for (Map<String, Object> entry : body) {
                Problem problem = problemRepo.findById(UUID.fromString((String) entry.get("problemId"))).orElseThrow();
                RoomProblem rp = roomProblemRepo.findByRoomIdAndProblemId(id, problem.getId()).orElseGet(RoomProblem::new);
                rp.setRoom(room); rp.setProblem(problem);
                rp.setPoints(entry.get("points") != null ? (Integer) entry.get("points") : 100);
                rp.setSortOrder(entry.get("sortOrder") != null ? (Integer) entry.get("sortOrder") : 0);
                roomProblemRepo.save(rp);
            }
            return ResponseEntity.<Object>ok(roomProblems(id));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/problems")
    public List<Map<String, Object>> roomProblems(@PathVariable UUID id) {
        return roomProblemRepo.findByRoomIdOrderBySortOrderAsc(id).stream()
                .map(rp -> Map.<String, Object>of(
                        "problemId", rp.getProblem().getId(), "slug", rp.getProblem().getSlug(),
                        "title", rp.getProblem().getTitle(), "difficulty", rp.getProblem().getDifficulty(),
                        "points", rp.getPoints(), "sortOrder", rp.getSortOrder()))
                .toList();
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<?> start(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return roomRepo.findById(id).map(r -> {
            if (!r.getHost().getId().equals(user.getId())) return ResponseEntity.status(403).<Object>build();
            r.setStatus("active"); r.setStartTime(java.time.LocalDateTime.now());
            r = roomRepo.save(r);
            ws.convertAndSend("/topic/room/" + r.getId() + "/lobby", Map.of("event", "started"));
            return ResponseEntity.<Object>ok(r);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/leaderboard")
    public List<Map<String, Object>> leaderboard(@PathVariable UUID id) {
        List<RoomParticipant> ranked = participantRepo.findByRoomIdOrderByScoreDesc(id);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            RoomParticipant p = ranked.get(i);
            result.add(Map.of("username", p.getUser().getUsername(), "score", p.getScore(), "rank", i + 1));
        }
        return result;
    }

    private void addParticipant(Room room, User user) {
        if (participantRepo.findByRoomIdAndUserId(room.getId(), user.getId()).isEmpty()) {
            RoomParticipant p = new RoomParticipant();
            p.setRoom(room); p.setUser(user);
            participantRepo.save(p);
        }
    }

    private String generateCode() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
