package com.offbyone.duel;

import com.offbyone.model.Problem;
import com.offbyone.model.Room;
import com.offbyone.model.RoomProblem;
import com.offbyone.repository.ProblemRepository;
import com.offbyone.repository.RoomProblemRepository;
import com.offbyone.repository.RoomRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Picks and broadcasts the next round's problem for 1v1 duel-mode rooms (problemCount == 1). */
@Service
public class DuelRoundService {
    private final RoomRepository roomRepo;
    private final RoomProblemRepository roomProblemRepo;
    private final ProblemRepository problemRepo;
    private final SimpMessagingTemplate ws;

    public DuelRoundService(RoomRepository roomRepo, RoomProblemRepository roomProblemRepo,
                             ProblemRepository problemRepo, SimpMessagingTemplate ws) {
        this.roomRepo = roomRepo; this.roomProblemRepo = roomProblemRepo;
        this.problemRepo = problemRepo; this.ws = ws;
    }

    @Transactional
    public void advanceRound(UUID roomId) {
        Room room = roomRepo.findById(roomId).orElseThrow();

        List<RoomProblem> history = roomProblemRepo.findByRoomIdOrderBySortOrderAsc(roomId);
        List<UUID> used = history.stream().map(rp -> rp.getProblem().getId()).toList();

        List<Problem> pool = new ArrayList<>(problemRepo.findByIsActiveTrue());
        pool.removeIf(p -> used.contains(p.getId()));
        // Bank exhausted — recycle the full pool rather than stall the duel.
        if (pool.isEmpty()) pool = new ArrayList<>(problemRepo.findByIsActiveTrue());
        if (pool.isEmpty()) return;
        Collections.shuffle(pool);
        Problem next = pool.get(0);

        RoomProblem rp = new RoomProblem();
        rp.setRoom(room); rp.setProblem(next); rp.setPoints(1); rp.setSortOrder(room.getRoundsPlayed());
        roomProblemRepo.save(rp);

        room.setCurrentProblem(next);
        room.setRoundsPlayed(room.getRoundsPlayed() + 1);
        roomRepo.save(room);

        ws.convertAndSend("/topic/room/" + roomId + "/lobby", Map.of(
            "event", "round_start",
            "round", room.getRoundsPlayed(),
            "problem", Map.of("id", next.getId(), "slug", next.getSlug(),
                    "title", next.getTitle(), "difficulty", next.getDifficulty())
        ));
    }
}
