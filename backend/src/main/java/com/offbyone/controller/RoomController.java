package com.offbyone.controller;

import com.offbyone.duel.DuelRoundService;
import com.offbyone.model.*;
import com.offbyone.repository.*;
import com.offbyone.sql.SqlProblem;
import com.offbyone.sql.SqlProblemBank;
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
    private final SqlProblemBank sqlProblemBank;
    private final SubmissionRepository submissionRepo;
    private final DuelRoundService duelRoundService;
    private final SimpMessagingTemplate ws;

    public RoomController(RoomRepository roomRepo, RoomParticipantRepository participantRepo,
                           RoomProblemRepository roomProblemRepo, ProblemRepository problemRepo,
                           SqlProblemBank sqlProblemBank, SubmissionRepository submissionRepo,
                           DuelRoundService duelRoundService, SimpMessagingTemplate ws) {
        this.roomRepo = roomRepo; this.participantRepo = participantRepo;
        this.roomProblemRepo = roomProblemRepo; this.problemRepo = problemRepo; this.sqlProblemBank = sqlProblemBank;
        this.submissionRepo = submissionRepo; this.duelRoundService = duelRoundService; this.ws = ws;
    }

    @PostMapping
    public ResponseEntity<Room> create(@RequestBody Map<String, String> body, @AuthenticationPrincipal User user) {
        Room room = new Room();
        room.setName(body.get("name")); room.setHost(user); room.setJoinCode(generateCode());
        String track = body.get("track");
        room.setTrack(List.of("java", "mixed", "sql").contains(track) ? track : "java");
        if ("duel".equals(body.get("mode"))) {
            room.setProblemCount(1);
        } else if (body.get("problemCount") != null) {
            int pc = Integer.parseInt(body.get("problemCount"));
            room.setProblemCount(Math.max(2, Math.min(6, pc)));
        }
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
        return roomProblemRepo.findByRoomIdOrderBySortOrderAsc(id).stream().map(this::roomProblemEntry).toList();
    }

    private String submissionKey(Submission s) {
        return s.getProblem() != null ? "java:" + s.getProblem().getId() : "sql:" + s.getSqlSlug();
    }

    private Map<String, Object> roomProblemEntry(RoomProblem rp) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (rp.isSql()) {
            SqlProblem p = sqlProblemBank.findBySlug(rp.getSqlSlug()).orElse(null);
            m.put("type", "sql"); m.put("slug", rp.getSqlSlug());
            m.put("title", p != null ? p.title() : rp.getSqlSlug());
            m.put("difficulty", p != null ? p.difficulty() : "medium");
        } else {
            m.put("type", "java"); m.put("problemId", rp.getProblem().getId()); m.put("slug", rp.getProblem().getSlug());
            m.put("title", rp.getProblem().getTitle()); m.put("difficulty", rp.getProblem().getDifficulty());
        }
        m.put("points", rp.getPoints()); m.put("sortOrder", rp.getSortOrder());
        return m;
    }

    @GetMapping("/{id}/state")
    @Transactional(readOnly = true)
    public ResponseEntity<?> state(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return roomRepo.findById(id).map(room -> {
            List<Submission> accepted = submissionRepo.findByRoomId(id).stream()
                    .filter(s -> "accepted".equals(s.getVerdict())).toList();

            Map<String, List<String>> solvedByProblem = new HashMap<>();
            for (Submission s : accepted) {
                solvedByProblem.computeIfAbsent(submissionKey(s), k -> new ArrayList<>()).add(s.getUser().getUsername());
            }

            List<Map<String, Object>> problems = roomProblemRepo.findByRoomIdOrderBySortOrderAsc(id).stream().map(rp -> {
                Map<String, Object> m = roomProblemEntry(rp);
                String key = rp.isSql() ? "sql:" + rp.getSqlSlug() : "java:" + rp.getProblem().getId();
                m.put("solvedBy", solvedByProblem.getOrDefault(key, List.of()));
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
                    .map(s -> s.getProblem() != null ? s.getProblem().getSlug() : s.getSqlSlug()).distinct().toList());

            Map<String, Object> roomInfo = new LinkedHashMap<>();
            roomInfo.put("id", room.getId());
            roomInfo.put("name", room.getName());
            roomInfo.put("joinCode", room.getJoinCode());
            roomInfo.put("status", room.getStatus());
            roomInfo.put("startTime", room.getStartTime());
            roomInfo.put("endTime", room.getEndTime());
            roomInfo.put("durationMinutes", room.getDurationMinutes());
            roomInfo.put("hostUsername", room.getHost() != null ? room.getHost().getUsername() : null);
            roomInfo.put("problemCount", room.getProblemCount());
            roomInfo.put("track", room.getTrack());
            roomInfo.put("isDuel", room.getProblemCount() == 1);
            roomInfo.put("roundsPlayed", room.getRoundsPlayed());
            boolean currentIsSql = room.getCurrentSqlSlug() != null;
            roomInfo.put("currentProblemType", currentIsSql ? "sql" : "java");
            roomInfo.put("currentProblemSlug", currentIsSql ? room.getCurrentSqlSlug()
                    : (room.getCurrentProblem() != null ? room.getCurrentProblem().getSlug() : null));

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
            int players = participantRepo.findByRoomIdOrderByScoreDesc(id).size();
            boolean isDuel = r.getProblemCount() == 1;

            if (isDuel && players != 2)
                return ResponseEntity.status(409).<Object>body(Map.of("error", "A duel needs exactly 2 players"));
            if (!isDuel && players < 2)
                return ResponseEntity.status(409).<Object>body(Map.of("error", "A duel needs at least 2 players"));

            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            r.setStatus("active"); r.setStartTime(now);
            if (isDuel) {
                r.setEndTime(null); // duel is round-based, no fixed end time
            } else {
                r.setEndTime(now.plusMinutes(r.getDurationMinutes()));
                if (roomProblemRepo.findByRoomIdOrderBySortOrderAsc(id).isEmpty()) autoAssignProblems(r);
            }
            r = roomRepo.save(r);
            ws.convertAndSend("/topic/room/" + r.getId() + "/lobby", Map.of("event", "started"));
            if (isDuel) duelRoundService.advanceRound(r.getId());
            return ResponseEntity.<Object>ok(r);
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Duel mode has no fixed end time — it ends when a player leaves (UI-SPEC.md §5). Tournament rooms just drop the participant and keep running. */
    @PostMapping("/{id}/leave")
    @Transactional
    public ResponseEntity<?> leave(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return roomRepo.findById(id).map(room -> {
            if ("finished".equals(room.getStatus())) return ResponseEntity.ok(Map.of("status", "finished"));

            boolean isDuel = room.getProblemCount() == 1;
            if (isDuel && "active".equals(room.getStatus())) {
                room.setStatus("finished");
                List<RoomParticipant> ranked = participantRepo.findByRoomIdOrderByScoreDescLastSolveAtAsc(id);
                ranked.stream().map(RoomParticipant::getUser)
                        .filter(u -> !u.getId().equals(user.getId())).findFirst()
                        .ifPresent(room::setWinner); // opponent wins by forfeit, regardless of score at the time
                roomRepo.save(room);
                List<Map<String, Object>> standings = new ArrayList<>();
                for (int i = 0; i < ranked.size(); i++) {
                    RoomParticipant p = ranked.get(i);
                    standings.add(Map.of("username", p.getUser().getUsername(), "score", p.getScore(),
                            "solvedCount", p.getSolvedCount(), "rank", i + 1,
                            "left", p.getUser().getId().equals(user.getId())));
                }
                ws.convertAndSend("/topic/room/" + id + "/finished", Map.of(
                        "event", "finished", "reason", "opponent_left",
                        "leftUsername", user.getUsername(), "standings", standings));
            } else {
                ws.convertAndSend("/topic/room/" + id + "/lobby", Map.of("event", "left", "username", user.getUsername()));
            }
            return ResponseEntity.ok(Map.of("status", room.getStatus()));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/next-problem")
    @Transactional
    public ResponseEntity<?> nextProblem(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return roomRepo.findById(id).map(r -> {
            if (!r.getHost().getId().equals(user.getId())) return ResponseEntity.status(403).<Object>build();
            if (r.getProblemCount() != 1) return ResponseEntity.badRequest().<Object>body(Map.of("error", "Not a duel room"));
            duelRoundService.advanceRound(id);
            return ResponseEntity.<Object>ok(Map.of("round", r.getRoundsPlayed()));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Track-aware: "java" stays Java-only, "sql" stays SQL-only, "mixed" alternates by slot parity
     * (BUILD-SPEC's north star: "rounds 1,3 Java / 2,5 SQL"). Falls back to whichever pool still has
     * problems if one runs short. */
    private void autoAssignProblems(Room room) {
        int count = room.getProblemCount();
        String track = room.getTrack();
        List<Problem> javaPool = "sql".equals(track) ? List.of() : new ArrayList<>(problemRepo.findByIsActiveTrue());
        Collections.shuffle(javaPool);
        List<SqlProblem> sqlPool = "java".equals(track) ? List.of() : new ArrayList<>(sqlProblemBank.findAll());
        Collections.shuffle(sqlPool);

        int javaIdx = 0, sqlIdx = 0, sortOrder = 0;
        for (int i = 0; i < count; i++) {
            boolean wantSql = "sql".equals(track) || (!"java".equals(track) && i % 2 == 1);
            RoomProblem rp = new RoomProblem();
            rp.setRoom(room); rp.setPoints(100);
            if (wantSql && sqlIdx < sqlPool.size()) {
                rp.setSqlSlug(sqlPool.get(sqlIdx++).slug());
            } else if (!wantSql && javaIdx < javaPool.size()) {
                rp.setProblem(javaPool.get(javaIdx++));
            } else if (javaIdx < javaPool.size()) {
                rp.setProblem(javaPool.get(javaIdx++));
            } else if (sqlIdx < sqlPool.size()) {
                rp.setSqlSlug(sqlPool.get(sqlIdx++).slug());
            } else {
                continue;
            }
            rp.setSortOrder(sortOrder++);
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
