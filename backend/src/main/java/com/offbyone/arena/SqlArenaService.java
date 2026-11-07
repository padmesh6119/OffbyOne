package com.offbyone.arena;

import com.offbyone.model.*;
import com.offbyone.repository.*;
import com.offbyone.sql.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

/** Database-serialized match transitions. Pollable snapshots survive reconnects and restarts. */
@Service
@Transactional
public class SqlArenaService {
    private final RoomRepository rooms;
    private final RoomParticipantRepository players;
    private final RoomProblemRepository assignments;
    private final SubmissionRepository submissions;
    private final SqlProblemBank bank;
    private final SqlJudge judge;
    private final SimpMessagingTemplate ws;
    public SqlArenaService(RoomRepository r, RoomParticipantRepository p, RoomProblemRepository a,
                           SubmissionRepository s, SqlProblemBank b, SqlJudge j,
                           @Autowired(required = false) SimpMessagingTemplate ws) {
        this.rooms=r; this.players=p; this.assignments=a; this.submissions=s; this.bank=b; this.judge=j; this.ws=ws;
    }
    private void broadcast(String topic, Object payload) {
        if (ws != null) {
            try {
                ws.convertAndSend(topic, payload);
            } catch (Exception ignored) {}
        }
    }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    private ResponseStatusException fail(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
    private Room locked(UUID id,User user) {
        Room room=rooms.lockById(id).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"Match not found."));
        if(!"sql-arena".equals(room.getTrack())) throw fail(HttpStatus.BAD_REQUEST,"Not a SQL arena match.");
        if(players.findByRoomIdAndUserId(id,user.getId()).isEmpty()) throw fail(HttpStatus.FORBIDDEN,"Join this match first.");
        return room;
    }
    private void add(Room r,User u) {
        if(players.findByRoomIdAndUserId(r.getId(),u.getId()).isPresent()) return;
        if(!"waiting".equals(r.getStatus()) || roster(r).size()>=2) throw fail(HttpStatus.CONFLICT,"This match is full or already started.");
        RoomParticipant p=new RoomParticipant();p.setRoom(r);p.setUser(u);p.setSkillLevel(u.getSkillLevel());players.save(p);
        broadcast("/topic/room/" + r.getId() + "/lobby", Map.of("event", "joined", "username", u.getDisplayName()));
    }
    private List<RoomParticipant> roster(Room r) { return players.findByRoomIdOrderByScoreDescLastSolveAtAsc(r.getId()); }
    public Map<String,Object> create(User user) { return snapshot(createRoom(user,null),user); }
    private Room createRoom(User user,String code) {
        Room r=new Room();r.setHost(user);r.setName("SQL Arena");r.setTrack("sql-arena");r.setProblemCount(1);
        r.setJoinCode(code==null ? UUID.randomUUID().toString().replace("-", "").substring(0,8).toUpperCase(Locale.ROOT) : code);
        r=rooms.save(r); add(r,user);return r;
    }
    public Map<String,Object> join(String code,User user) {
        Room found=rooms.findByJoinCode(code.trim().toUpperCase(Locale.ROOT)).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"No match with that code."));
        Room room=rooms.lockById(found.getId()).orElseThrow();
        if(!"sql-arena".equals(room.getTrack())) throw fail(HttpStatus.BAD_REQUEST,"Use a SQL arena invite code.");
        add(room,user);return snapshot(room,user);
    }
    public Map<String,Object> state(UUID id,User user) { Room r=locked(id,user);tick(r);return snapshot(r,user); }
    public Map<String,Object> skill(UUID id, User user, int level) {
        Room r = locked(id, user);
        if (!"waiting".equals(r.getStatus())) throw fail(HttpStatus.CONFLICT, "Skill levels are locked once the match starts.");
        if (level < 1 || level > 10) throw fail(HttpStatus.BAD_REQUEST, "Choose a level from 1 to 10.");
        roster(r).stream().filter(p -> p.getUser().getId().equals(user.getId())).findFirst().orElseThrow().setSkillLevel(level);
        broadcast("/topic/room/" + id + "/lobby", Map.of("event", "skill_updated", "username", user.getDisplayName(), "level", level));
        return snapshot(r, user);
    }
    public Map<String,Object> start(UUID id,User user) {
        Room r=locked(id,user);
        if(!r.getHost().getId().equals(user.getId())) throw fail(HttpStatus.FORBIDDEN,"Only the host can start.");
        if(!"waiting".equals(r.getStatus()) || roster(r).size()!=2) throw fail(HttpStatus.CONFLICT,"Two players must be in the lobby.");
        List<SqlProblem> pool=new ArrayList<>(bank.findAll());Collections.shuffle(pool);
        int level = (int)Math.round(roster(r).stream().mapToInt(p -> p.getSkillLevel()).average().orElse(5));
        String difficulty = level <= 3 ? "easy" : level <= 7 ? "medium" : "hard";
        List<SqlProblem> selected = pool.stream().sorted(Comparator.comparingInt(p -> Math.abs(List.of("easy","medium","hard").indexOf(p.difficulty()) - List.of("easy","medium","hard").indexOf(difficulty)))).limit(3).toList();
        if (selected.size() < 3) throw fail(HttpStatus.SERVICE_UNAVAILABLE,"Not enough challenges at this skill level.");
        for(int round = 0; round < selected.size(); round++) {
            RoomProblem rp=new RoomProblem();rp.setRoom(r);rp.setSqlSlug(selected.get(round).slug());rp.setSortOrder(round);rp.setPoints(1);assignments.save(rp);
        }
        r.setRoundsPlayed(1);r.setStartTime(now());activate(r);
        broadcast("/topic/room/" + id + "/lobby", Map.of("event", "started", "round", 1));
        return snapshot(r,user);
    }
    private void activate(Room r) {
        var list=assignments.findByRoomIdOrderBySortOrderAsc(r.getId());
        r.setCurrentSqlSlug(list.get(r.getRoundsPlayed()-1).getSqlSlug());r.setStatus("active");r.setEndTime(now().plusMinutes(5));rooms.save(r);
        broadcast("/topic/room/" + r.getId() + "/lobby", Map.of("event", "round_start", "round", r.getRoundsPlayed()));
    }
    private void finish(Room r) {
        var list=roster(r);r.setStatus("finished");r.setEndTime(now());
        r.setWinner(list.size()==2 && list.get(0).getScore()!=list.get(1).getScore() ? list.get(0).getUser() : null);rooms.save(r);
        broadcast("/topic/room/" + r.getId() + "/finished", Map.of("event", "finished", "winner", r.getWinner()==null ? "" : r.getWinner().getDisplayName()));
    }
    private void endRound(Room r) {
        if(r.getRoundsPlayed()>=3 || roster(r).stream().anyMatch(p->p.getScore()>=2)) finish(r);
        else {
            r.setStatus("intermission");r.setEndTime(now().plusSeconds(5));rooms.save(r);
            broadcast("/topic/room/" + r.getId() + "/lobby", Map.of("event", "intermission", "round", r.getRoundsPlayed()));
        }
    }
    private void tick(Room r) {
        if(r.getEndTime()==null || now().isBefore(r.getEndTime())) return;
        if("active".equals(r.getStatus())) endRound(r);
        else if("intermission".equals(r.getStatus())) {r.setRoundsPlayed(r.getRoundsPlayed()+1);activate(r);}
    }
    public Map<String,Object> submit(UUID id,User user,int round,String query) {
        if(query==null || query.isBlank() || query.length()>16000) throw fail(HttpStatus.BAD_REQUEST,"Write a query of 1–16,000 characters.");
        Room r=locked(id,user);tick(r);
        if(!"active".equals(r.getStatus()) || r.getRoundsPlayed()!=round) throw fail(HttpStatus.CONFLICT,"This round has ended. Your match has been refreshed.");
        SqlProblem problem=bank.findBySlug(r.getCurrentSqlSlug()).orElseThrow();
        SqlJudge.Verdict v=judge.judge(problem,query);
        if("busy".equals(v.status())) throw fail(HttpStatus.TOO_MANY_REQUESTS,v.message());
        Submission s=new Submission();s.setRoom(r);s.setUser(user);s.setSqlSlug(problem.slug());s.setLanguage("sql");s.setCode(query==null ? "" : query);s.setVerdict(v.status());submissions.save(s);
        broadcast("/topic/room/" + id + "/submission", Map.of("username", user.getDisplayName(), "verdict", v.status(), "round", round));
        if("accepted".equals(v.status())) {
            RoomParticipant p=players.findByRoomIdAndUserId(id,user.getId()).orElseThrow();
            p.setScore(p.getScore()+1);p.setSolvedCount(p.getSolvedCount()+1);p.setLastSolveAt(now());players.save(p);
            broadcast("/topic/room/" + id + "/lobby", Map.of("event", "round_won", "winner", user.getDisplayName(), "round", round));
            endRound(r);
        }
        return Map.of("result",v,"match",snapshot(r,user));
    }
    public Map<String,Object> leave(UUID id,User user) {
        Room r=locked(id,user);tick(r);
        if(!"finished".equals(r.getStatus())) {
            r.setStatus("finished");r.setEndTime(now());
            r.setWinner(roster(r).stream().filter(p->!p.getUser().getId().equals(user.getId())).map(RoomParticipant::getUser).findFirst().orElse(null));rooms.save(r);
            broadcast("/topic/room/" + id + "/finished", Map.of("event", "finished", "winner", r.getWinner()==null ? "" : r.getWinner().getDisplayName()));
        }
        broadcast("/topic/room/" + id + "/lobby", Map.of("event", "left", "username", user.getDisplayName()));
        return snapshot(r,user);
    }
    public Map<String,Object> rematch(UUID id,User user) {
        Room r=locked(id,user);
        if(!"finished".equals(r.getStatus())) throw fail(HttpStatus.CONFLICT,"Finish this match first.");
        String code="R"+r.getId().toString().replace("-", "").toUpperCase(Locale.ROOT);
        Room next=rooms.findByJoinCode(code).orElseGet(()->createRoom(user,code));
        next=rooms.lockById(next.getId()).orElseThrow();add(next,user);
        broadcast("/topic/room/" + id + "/lobby", Map.of("event", "rematch", "code", next.getJoinCode(), "nextId", next.getId().toString()));
        return snapshot(next,user);
    }
    private Map<String,Object> snapshot(Room r,User user) {
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("id",r.getId());out.put("code",r.getJoinCode());out.put("status",r.getStatus());out.put("round",r.getRoundsPlayed());
        out.put("serverTime",now().toInstant(ZoneOffset.UTC).toString());
        out.put("deadline",r.getEndTime()==null ? null : r.getEndTime().toInstant(ZoneOffset.UTC).toString());
        out.put("host",r.getHost().getDisplayName());out.put("winner",r.getWinner()==null ? null : r.getWinner().getDisplayName());
        out.put("players",roster(r).stream().map(p->Map.of("username",p.getUser().getDisplayName(),"score",p.getScore(),"skillLevel",p.getSkillLevel())).toList());
        var attempts=submissions.findByRoomId(r.getId());
        List<Map<String,Object>> history=new ArrayList<>();
        for(RoomProblem rp:assignments.findByRoomIdOrderBySortOrderAsc(r.getId())) {
            int round=rp.getSortOrder()+1;
            if(round>r.getRoundsPlayed()) continue;
            var p=bank.findBySlug(rp.getSqlSlug()).orElseThrow();
            Map<String,Object> h=new LinkedHashMap<>();h.put("round",round);h.put("title",p.title());
            h.put("winner",attempts.stream().filter(s->p.slug().equals(s.getSqlSlug()) && "accepted".equals(s.getVerdict())).map(s->s.getUser().getDisplayName()).findFirst().orElse(null));
            if("finished".equals(r.getStatus())) {h.put("explanation",p.explanation());h.put("solution",p.referenceQuery());}
            history.add(h);
        }
        out.put("skillLevel", (int)Math.round(roster(r).stream().mapToInt(p -> p.getSkillLevel()).average().orElse(5)));
        out.put("history",history);
        out.put("myAttempts",attempts.stream().filter(s->s.getUser().getId().equals(user.getId()) && Objects.equals(s.getSqlSlug(),r.getCurrentSqlSlug()))
            .sorted(Comparator.comparing(Submission::getSubmittedAt).reversed()).limit(5).map(s->Map.of("verdict",s.getVerdict(),"at",s.getSubmittedAt())).toList());
        if(r.getCurrentSqlSlug()!=null) {
            SqlProblem p=bank.findBySlug(r.getCurrentSqlSlug()).orElseThrow();
            out.put("problem",Map.of("slug",p.slug(),"title",p.title(),"task",p.task(),"difficulty",p.difficulty(),"pattern",p.pattern(),"schema",p.schema(),"tables",judge.previewTables(p)));
        }
        return out;
    }
}
