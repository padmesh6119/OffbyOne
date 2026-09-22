package com.offbyone.duel;

import com.offbyone.model.Room;
import com.offbyone.model.RoomParticipant;
import com.offbyone.repository.RoomParticipantRepository;
import com.offbyone.repository.RoomProblemRepository;
import com.offbyone.repository.RoomRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DuelSweeper {
    private final RoomRepository roomRepo;
    private final RoomParticipantRepository participantRepo;
    private final RoomProblemRepository roomProblemRepo;
    private final SimpMessagingTemplate ws;

    public DuelSweeper(RoomRepository roomRepo, RoomParticipantRepository participantRepo,
                        RoomProblemRepository roomProblemRepo, SimpMessagingTemplate ws) {
        this.roomRepo = roomRepo; this.participantRepo = participantRepo;
        this.roomProblemRepo = roomProblemRepo; this.ws = ws;
    }

    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void sweep() {
        for (Room room : roomRepo.findByStatus("active")) {
            boolean timeUp = room.getEndTime() != null && LocalDateTime.now().isAfter(room.getEndTime());
            int totalProblems = roomProblemRepo.findByRoomIdOrderBySortOrderAsc(room.getId()).size();
            List<RoomParticipant> ranked = participantRepo.findByRoomIdOrderByScoreDescLastSolveAtAsc(room.getId());
            boolean allSolved = totalProblems > 0 && ranked.stream().anyMatch(p -> p.getSolvedCount() >= totalProblems);

            if (!timeUp && !allSolved) continue;

            room.setStatus("finished");
            roomRepo.save(room);

            List<Map<String, Object>> standings = new ArrayList<>();
            for (int i = 0; i < ranked.size(); i++) {
                RoomParticipant p = ranked.get(i);
                standings.add(Map.of("username", p.getUser().getUsername(), "score", p.getScore(),
                        "solvedCount", p.getSolvedCount(), "rank", i + 1));
            }
            ws.convertAndSend("/topic/room/" + room.getId() + "/finished",
                    Map.of("event", "finished", "standings", standings));
        }
    }
}
