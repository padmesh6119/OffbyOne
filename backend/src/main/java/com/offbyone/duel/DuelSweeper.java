package com.offbyone.duel;

import com.offbyone.model.RatingHistory;
import com.offbyone.model.Room;
import com.offbyone.model.RoomParticipant;
import com.offbyone.model.User;
import com.offbyone.repository.RatingHistoryRepository;
import com.offbyone.repository.RoomParticipantRepository;
import com.offbyone.repository.RoomProblemRepository;
import com.offbyone.repository.RoomRepository;
import com.offbyone.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class DuelSweeper {
    private static final int ELO_K = 32;

    private final RoomRepository roomRepo;
    private final RoomParticipantRepository participantRepo;
    private final RoomProblemRepository roomProblemRepo;
    private final UserRepository userRepo;
    private final RatingHistoryRepository ratingHistoryRepo;
    private final SimpMessagingTemplate ws;

    public DuelSweeper(RoomRepository roomRepo, RoomParticipantRepository participantRepo,
                        RoomProblemRepository roomProblemRepo, UserRepository userRepo,
                        RatingHistoryRepository ratingHistoryRepo, SimpMessagingTemplate ws) {
        this.roomRepo = roomRepo; this.participantRepo = participantRepo;
        this.roomProblemRepo = roomProblemRepo; this.userRepo = userRepo;
        this.ratingHistoryRepo = ratingHistoryRepo; this.ws = ws;
    }

    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void sweep() {
        for (Room room : roomRepo.findByStatus("active")) {
            boolean timeUp = room.getEndTime() != null && LocalDateTime.now().isAfter(room.getEndTime());
            List<RoomParticipant> ranked = participantRepo.findByRoomIdOrderByScoreDescLastSolveAtAsc(room.getId());
            // Round-based duel rooms (problemCount == 1) are infinite by design — the problem pool
            // grows every round, so "solvedCount >= totalProblems" would false-trigger the moment
            // a player wins every round so far. Only tournament-mode rooms auto-finish this way.
            boolean allSolved = false;
            if (room.getProblemCount() > 1) {
                int totalProblems = roomProblemRepo.findByRoomIdOrderBySortOrderAsc(room.getId()).size();
                allSolved = totalProblems > 0 && ranked.stream().anyMatch(p -> p.getSolvedCount() >= totalProblems);
            }

            if (!timeUp && !allSolved) continue;

            room.setStatus("finished");
            if (!ranked.isEmpty()) room.setWinner(ranked.get(0).getUser());
            roomRepo.save(room);

            Map<UUID, Integer> ratingDeltas = applyElo(room, ranked);

            List<Map<String, Object>> standings = new ArrayList<>();
            for (int i = 0; i < ranked.size(); i++) {
                RoomParticipant p = ranked.get(i);
                standings.add(Map.of("username", p.getUser().getUsername(), "score", p.getScore(),
                        "solvedCount", p.getSolvedCount(), "rank", i + 1,
                        "ratingDelta", ratingDeltas.getOrDefault(p.getUser().getId(), 0),
                        "rating", p.getUser().getRating()));
            }
            ws.convertAndSend("/topic/room/" + room.getId() + "/finished",
                    Map.of("event", "finished", "standings", standings));
        }
    }

    /** Pairwise Elo (K=32): each participant plays every other as a virtual 1v1, decided by final score. */
    private Map<UUID, Integer> applyElo(Room room, List<RoomParticipant> ranked) {
        Map<UUID, Integer> deltas = new HashMap<>();
        if (ranked.size() < 2) return deltas;

        for (RoomParticipant a : ranked) {
            double sum = 0;
            for (RoomParticipant b : ranked) {
                if (a == b) continue;
                double expected = 1.0 / (1.0 + Math.pow(10, (b.getUser().getRating() - a.getUser().getRating()) / 400.0));
                double actual = a.getScore() > b.getScore() ? 1.0 : a.getScore() < b.getScore() ? 0.0 : 0.5;
                sum += ELO_K * (actual - expected);
            }
            deltas.put(a.getUser().getId(), (int) Math.round(sum / (ranked.size() - 1)));
        }
        for (RoomParticipant p : ranked) {
            User u = p.getUser();
            int delta = deltas.get(u.getId());
            u.setRating(u.getRating() + delta);
            userRepo.save(u);

            RatingHistory rh = new RatingHistory();
            rh.setUser(u); rh.setRoom(room); rh.setRating(u.getRating()); rh.setDelta(delta);
            ratingHistoryRepo.save(rh);
        }
        return deltas;
    }
}
