package com.offbyone.controller;

import com.offbyone.model.RatingHistory;
import com.offbyone.model.Room;
import com.offbyone.model.RoomParticipant;
import com.offbyone.model.Submission;
import com.offbyone.model.User;
import com.offbyone.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/users")
public class ProfileController {
    private final UserRepository userRepo;
    private final RoomParticipantRepository participantRepo;
    private final SubmissionRepository submissionRepo;
    private final RatingHistoryRepository ratingHistoryRepo;

    public ProfileController(UserRepository userRepo, RoomParticipantRepository participantRepo,
                              SubmissionRepository submissionRepo, RatingHistoryRepository ratingHistoryRepo) {
        this.userRepo = userRepo; this.participantRepo = participantRepo;
        this.submissionRepo = submissionRepo; this.ratingHistoryRepo = ratingHistoryRepo;
    }

    @GetMapping("/{username}/profile")
    @Transactional(readOnly = true)
    public ResponseEntity<?> profile(@PathVariable String username) {
        return userRepo.findByUsername(username).map(user -> {
            List<RatingHistory> history = ratingHistoryRepo.findByUserIdOrderByCreatedAtAsc(user.getId());
            List<Map<String, Object>> ratingHistory = history.stream()
                    .map(h -> Map.<String, Object>of("rating", h.getRating(), "delta", h.getDelta(), "at", h.getCreatedAt()))
                    .toList();

            List<Submission> accepted = submissionRepo.findByUserId(user.getId()).stream()
                    .filter(s -> "accepted".equals(s.getVerdict())).toList();
            long solvedJava = accepted.stream().filter(s -> s.getProblem() != null)
                    .map(s -> s.getProblem().getId()).distinct().count();
            long solvedSql = accepted.stream().filter(s -> s.getSqlSlug() != null)
                    .map(Submission::getSqlSlug).distinct().count();

            int wins = 0, losses = 0;
            for (RoomParticipant rp : participantRepo.findByUserId(user.getId())) {
                Room room = rp.getRoom();
                if (!"finished".equals(room.getStatus()) || room.getWinner() == null) continue;
                if (room.getWinner().getId().equals(user.getId())) wins++; else losses++;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("username", user.getUsername());
            result.put("rating", user.getRating());
            result.put("ratingHistory", ratingHistory);
            result.put("solvedJava", solvedJava);
            result.put("solvedSql", solvedSql);
            result.put("wins", wins);
            result.put("losses", losses);
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Head-to-head record per opponent. Duel results use the room's forfeit-aware winner (score at
     * the moment of leaving isn't reliable); tournament results compare the pair's scores directly,
     * since "the room winner" doesn't capture a pairwise result among 3+ players. */
    @GetMapping("/{username}/rivals")
    @Transactional(readOnly = true)
    public ResponseEntity<?> rivals(@PathVariable String username) {
        return userRepo.findByUsername(username).map(user -> {
            Map<String, int[]> tally = new LinkedHashMap<>(); // username -> [wins, losses]

            for (RoomParticipant mine : participantRepo.findByUserId(user.getId())) {
                Room room = mine.getRoom();
                if (!"finished".equals(room.getStatus())) continue;
                boolean isDuel = room.getProblemCount() == 1;
                if (isDuel && room.getWinner() == null) continue;

                for (RoomParticipant other : participantRepo.findByRoomIdOrderByScoreDesc(room.getId())) {
                    if (other.getUser().getId().equals(user.getId())) continue;
                    Boolean iWon;
                    if (isDuel) {
                        iWon = room.getWinner().getId().equals(user.getId());
                    } else if (mine.getScore() == other.getScore()) {
                        iWon = null; // tie — doesn't count either way
                    } else {
                        iWon = mine.getScore() > other.getScore();
                    }
                    if (iWon == null) continue;
                    int[] rec = tally.computeIfAbsent(other.getUser().getUsername(), k -> new int[2]);
                    if (iWon) rec[0]++; else rec[1]++;
                }
            }

            List<Map<String, Object>> rivals = tally.entrySet().stream()
                    .map(e -> Map.<String, Object>of("username", e.getKey(), "wins", e.getValue()[0],
                            "losses", e.getValue()[1], "matches", e.getValue()[0] + e.getValue()[1]))
                    .sorted((a, b) -> (Integer) b.get("matches") - (Integer) a.get("matches"))
                    .toList();
            return ResponseEntity.ok(rivals);
        }).orElse(ResponseEntity.notFound().build());
    }
}
