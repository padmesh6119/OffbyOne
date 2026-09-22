package com.offbyone.duel;

import com.offbyone.model.Problem;
import com.offbyone.model.Room;
import com.offbyone.model.RoomProblem;
import com.offbyone.repository.ProblemRepository;
import com.offbyone.repository.RoomProblemRepository;
import com.offbyone.repository.RoomRepository;
import com.offbyone.sql.SqlProblem;
import com.offbyone.sql.SqlProblemBank;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Picks and broadcasts the next round's problem for 1v1 duel-mode rooms (problemCount == 1).
 * Pool composition follows Room.track: "java" stays Java-only, "sql" stays SQL-only, "mixed"
 * draws from both (BUILD-SPEC's "never single-track" north star). */
@Service
public class DuelRoundService {
    private final RoomRepository roomRepo;
    private final RoomProblemRepository roomProblemRepo;
    private final ProblemRepository problemRepo;
    private final SqlProblemBank sqlProblemBank;
    private final SimpMessagingTemplate ws;

    public DuelRoundService(RoomRepository roomRepo, RoomProblemRepository roomProblemRepo,
                             ProblemRepository problemRepo, SqlProblemBank sqlProblemBank, SimpMessagingTemplate ws) {
        this.roomRepo = roomRepo; this.roomProblemRepo = roomProblemRepo;
        this.problemRepo = problemRepo; this.sqlProblemBank = sqlProblemBank; this.ws = ws;
    }

    private record Candidate(boolean sql, Problem javaProblem, SqlProblem sqlProblem) {}

    @Transactional
    public void advanceRound(UUID roomId) {
        Room room = roomRepo.findById(roomId).orElseThrow();

        List<RoomProblem> history = roomProblemRepo.findByRoomIdOrderBySortOrderAsc(roomId);
        List<UUID> usedJava = history.stream().filter(rp -> !rp.isSql()).map(rp -> rp.getProblem().getId()).toList();
        List<String> usedSql = history.stream().filter(RoomProblem::isSql).map(RoomProblem::getSqlSlug).toList();

        String track = room.getTrack();
        List<Candidate> pool = buildPool(usedJava, usedSql, track);
        // Bank exhausted — recycle the full pool for this track rather than stall the duel.
        if (pool.isEmpty()) pool = buildPool(List.of(), List.of(), track);
        if (pool.isEmpty()) return;
        Collections.shuffle(pool);
        Candidate next = pool.get(0);

        RoomProblem rp = new RoomProblem();
        rp.setRoom(room); rp.setPoints(1); rp.setSortOrder(room.getRoundsPlayed());

        Map<String, Object> problemPayload;
        if (next.sql()) {
            SqlProblem p = next.sqlProblem();
            rp.setSqlSlug(p.slug());
            room.setCurrentProblem(null);
            room.setCurrentSqlSlug(p.slug());
            problemPayload = Map.of("type", "sql", "slug", p.slug(), "title", p.title(), "difficulty", p.difficulty());
        } else {
            Problem p = next.javaProblem();
            rp.setProblem(p);
            room.setCurrentProblem(p);
            room.setCurrentSqlSlug(null);
            problemPayload = Map.of("type", "java", "id", p.getId(), "slug", p.getSlug(),
                    "title", p.getTitle(), "difficulty", p.getDifficulty());
        }
        roomProblemRepo.save(rp);

        room.setRoundsPlayed(room.getRoundsPlayed() + 1);
        roomRepo.save(room);

        ws.convertAndSend("/topic/room/" + roomId + "/lobby", Map.of(
            "event", "round_start", "round", room.getRoundsPlayed(), "problem", problemPayload));
    }

    private List<Candidate> buildPool(List<UUID> usedJava, List<String> usedSql, String track) {
        List<Candidate> pool = new ArrayList<>();
        if (!"sql".equals(track)) {
            for (Problem p : problemRepo.findByIsActiveTrue()) {
                if (!usedJava.contains(p.getId())) pool.add(new Candidate(false, p, null));
            }
        }
        if (!"java".equals(track)) {
            for (SqlProblem p : sqlProblemBank.findAll()) {
                if (!usedSql.contains(p.slug())) pool.add(new Candidate(true, null, p));
            }
        }
        return pool;
    }
}
