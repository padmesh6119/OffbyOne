package com.offbyone.controller;

import com.offbyone.model.*;
import com.offbyone.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomRepository roomRepo;
    private final RoomParticipantRepository participantRepo;
    private final RoomProblemRepository roomProblemRepo;
    private final ProblemRepository problemRepo;
    private final SubmissionRepository submissionRepo;
    private final SimpMessagingTemplate ws;

    public RoomController(RoomRepository roomRepo, RoomParticipantRepository participantRepo,
                           RoomProblemRepository roomProblemRepo, ProblemRepository problemRepo,
                           SubmissionRepository submissionRepo, SimpMessagingTemplate ws) {
        this.roomRepo = roomRepo; this.participantRepo = participantRepo;
        this.roomProblemRepo = roomProblemRepo; this.problemRepo = problemRepo;
        this.submissionRepo = submissionRepo; this.ws = ws;
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
    @Transactional
    public ResponseEntity<?> join(@PathVariable String code, @AuthenticationPrincipal User user) {
        return roomRepo.findByJoinCode(code).map(r -> {
            addParticipant(r, user);
            ws.convertAndSend("/topic/room/" + r.getId() + "/lobby", Map.of("event", "joined", "username", user.getUsername()));
            return ResponseEntity.<Object>ok(Map.of("roomId", r.getId(), "name", r.getName(), "joinCode", r.getJoinCode(), "status", r.getStatus()));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/participants")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> participants(@PathVariable UUID id) {
        return participantRepo.findByRoomIdOrderByScoreDesc(id).stream()
                .map(p -> Map.<String, Object>of("userId", p.getUser().getId(), "username", p.getUser().getUsername(), "score", p.getScore()))
                .toList();
    }

    @PostMapping("/{id}/problems")
    @Transactional
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
    @Transactional(readOnly = true)
    public List<Map<String, Object>> roomProblems(@PathVariable UUID id) {
        return roomProblemRepo.findByRoomIdOrderBySortOrderAsc(id).stream()
                .map(rp -> Map.<String, Object>of(
                        "problemId", rp.getProblem().getId(), "slug", rp.getProblem().getSlug(),
                        "title", rp.getProblem().getTitle(), "difficulty", rp.getProblem().getDifficulty(),
                        "points", rp.getPoints(), "sortOrder", rp.getSortOrder()))
                .toList();
    }

    @GetMapping("/{id}/state")
    @Transactional(readOnly = true)
    public ResponseEntity<?> state(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return roomRepo.findById(id).map(room -> {
            List<Submission> accepted = submissionRepo.findByRoomId(id).stream()
                    .filter(s -> "accepted".equals(s.getVerdict())).toList();

            Map<UUID, List<String>> solvedByProblem = new HashMap<>();
            for (Submission s : accepted) {
                solvedByProblem.computeIfAbsent(s.getProblem().getId(), k -> new ArrayList<>()).add(s.getUser().getUsername());
            }

            List<Map<String, Object>> problems = roomProblemRepo.findByRoomIdOrderBySortOrderAsc(id).stream().map(rp -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("problemId", rp.getProblem().getId());
                m.put("slug", rp.getProblem().getSlug());
                m.put("title", rp.getProblem().getTitle());
                m.put("difficulty", rp.getProblem().getDifficulty());
                m.put("points", rp.getPoints());
                m.put("sortOrder", rp.getSortOrder());
                m.put("solvedBy", solvedByProblem.getOrDefault(rp.getProblem().getId(), List.of()));
                return m;
            }).toList();

            List<RoomParticipant> ranked = participantRepo.findByRoomIdOrderByScoreDescLastSolveAtAsc(id);
            List<Map<String, Object>> leaderboardList = new ArrayList<>();
            for (int i = 0; i < ranked.size(); i++) {
                RoomParticipant p = ranked.get(i);
                leaderboardList.add(Map.of("username", p.getUser().getUsername(), "score", p.getScore(),
                        "solvedCount", p.getSolvedCount(), "rank", i + 1));
            }

            RoomParticipant mine = participantRepo.findByRoomIdAndUserId(id, user.getId()).orElse(null);
            Map<String, Object> me = new LinkedHashMap<>();
            me.put("username", user.getUsername());
            me.put("score", mine != null ? mine.getScore() : 0);
            me.put("solved", accepted.stream().filter(s -> s.getUser().getId().equals(user.getId()))
                    .map(s -> s.getProblem().getSlug()).distinct().toList());

            Map<String, Object> roomInfo = new LinkedHashMap<>();
            roomInfo.put("id", room.getId());
            roomInfo.put("name", room.getName());
            roomInfo.put("joinCode", room.getJoinCode());
            roomInfo.put("status", room.getStatus());
            roomInfo.put("startTime", room.getStartTime());
            roomInfo.put("endTime", room.getEndTime());
            roomInfo.put("durationMinutes", room.getDurationMinutes());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("room", roomInfo);
            result.put("problems", problems);
            result.put("leaderboard", leaderboardList);
            result.put("me", me);
            return ResponseEntity.<Object>ok(result);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/start")
    @Transactional
    public ResponseEntity<?> start(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return roomRepo.findById(id).map(r -> {
            if (!r.getHost().getId().equals(user.getId())) return ResponseEntity.status(403).<Object>build();
            if (participantRepo.findByRoomIdOrderByScoreDesc(id).size() < 2)
                return ResponseEntity.status(409).<Object>body(Map.of("error", "A duel needs at least 2 players"));

            if (roomProblemRepo.findByRoomIdOrderBySortOrderAsc(id).isEmpty()) autoAssignProblems(r);

            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            r.setStatus("active"); r.setStartTime(now); r.setEndTime(now.plusMinutes(r.getDurationMinutes()));
            r = roomRepo.save(r);
            ws.convertAndSend("/topic/room/" + r.getId() + "/lobby", Map.of("event", "started"));
            return ResponseEntity.<Object>ok(r);
        }).orElse(ResponseEntity.notFound().build());
    }

    private void autoAssignProblems(Room room) {
        int count = room.getProblemCount();
        int easyTarget = Math.round(count * 0.4f);
        int mediumTarget = Math.round(count * 0.4f);
        int hardTarget = count - easyTarget - mediumTarget;

        List<Problem> easy = new ArrayList<>(problemRepo.findByDifficultyAndIsActiveTrue("easy"));
        List<Problem> medium = new ArrayList<>(problemRepo.findByDifficultyAndIsActiveTrue("medium"));
        List<Problem> hard = new ArrayList<>(problemRepo.findByDifficultyAndIsActiveTrue("hard"));
        Collections.shuffle(easy); Collections.shuffle(medium); Collections.shuffle(hard);

        List<Problem> selected = new ArrayList<>();
        selected.addAll(easy.subList(0, Math.min(easyTarget, easy.size())));
        selected.addAll(medium.subList(0, Math.min(mediumTarget, medium.size())));
        selected.addAll(hard.subList(0, Math.min(hardTarget, hard.size())));

        if (selected.size() < count) {
            List<Problem> leftover = new ArrayList<>(problemRepo.findByIsActiveTrue());
            leftover.removeAll(selected);
            Collections.shuffle(leftover);
            for (Problem p : leftover) {
                if (selected.size() >= count) break;
                selected.add(p);
            }
        }
        Collections.shuffle(selected);

        for (int i = 0; i < selected.size(); i++) {
            RoomProblem rp = new RoomProblem();
            rp.setRoom(room); rp.setProblem(selected.get(i)); rp.setPoints(100); rp.setSortOrder(i);
            roomProblemRepo.save(rp);
        }
    }

    @GetMapping("/{id}/leaderboard")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> leaderboard(@PathVariable UUID id) {
        List<RoomParticipant> ranked = participantRepo.findByRoomIdOrderByScoreDescLastSolveAtAsc(id);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            RoomParticipant p = ranked.get(i);
            result.add(Map.of("username", p.getUser().getUsername(), "score", p.getScore(),
                    "solvedCount", p.getSolvedCount(), "rank", i + 1));
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
