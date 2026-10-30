package com.offbyone.arena;

import com.offbyone.model.*;
import com.offbyone.repository.*;
import com.offbyone.sql.*;
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
    public SqlArenaService(RoomRepository r, RoomParticipantRepository p, RoomProblemRepository a,
                           SubmissionRepository s, SqlProblemBank b, SqlJudge j) {
        rooms=r; players=p; assignments=a; submissions=s; bank=b; judge=j;
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
        RoomParticipant p=new RoomParticipant();p.setRoom(r);p.setUser(u);players.save(p);
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
    public Map<String,Object> start(UUID id,User user) {
        Room r=locked(id,user);
        if(!r.getHost().getId().equals(user.getId())) throw fail(HttpStatus.FORBIDDEN,"Only the host can start.");
        if(!"waiting".equals(r.getStatus()) || roster(r).size()!=2) throw fail(HttpStatus.CONFLICT,"Two players must be in the lobby.");
        List<SqlProblem> pool=new ArrayList<>(bank.findAll());Collections.shuffle(pool);
        // One easy warm-up, one medium challenge, one harder deciding round.
        List<String> used=new ArrayList<>();
        for(String difficulty:List.of("easy","medium","hard")) {
            SqlProblem p=pool.stream().filter(x->difficulty.equals(x.difficulty()) && !used.contains(x.slug())).findFirst().orElseThrow();
            RoomProblem rp=new RoomProblem();rp.setRoom(r);rp.setSqlSlug(p.slug());rp.setSortOrder(used.size());rp.setPoints(1);assignments.save(rp);used.add(p.slug());
        }
        r.setRoundsPlayed(1);r.setStartTime(now());activate(r);return snapshot(r,user);
    }
    private void activate(Room r) {
        var list=assignments.findByRoomIdOrderBySortOrderAsc(r.getId());
        r.setCurrentSqlSlug(list.get(r.getRoundsPlayed()-1).getSqlSlug());r.setStatus("active");r.setEndTime(now().plusMinutes(5));rooms.save(r);
    }
    private void finish(Room r) {
        var list=roster(r);r.setStatus("finished");r.setEndTime(now());
        r.setWinner(list.size()==2 && list.get(0).getScore()!=list.get(1).getScore() ? list.get(0).getUser() : null);rooms.save(r);
    }
    private void endRound(Room r) {
        if(r.getRoundsPlayed()>=3 || roster(r).stream().anyMatch(p->p.getScore()>=2)) finish(r);
        else {r.setStatus("intermission");r.setEndTime(now().plusSeconds(5));rooms.save(r);}
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
        if("accepted".equals(v.status())) {
            RoomParticipant p=players.findByRoomIdAndUserId(id,user.getId()).orElseThrow();
            p.setScore(p.getScore()+1);p.setSolvedCount(p.getSolvedCount()+1);p.setLastSolveAt(now());players.save(p);endRound(r);
        }
        return Map.of("result",v,"match",snapshot(r,user));
    }
    public Map<String,Object> leave(UUID id,User user) {
        Room r=locked(id,user);tick(r);
        if(!"finished".equals(r.getStatus())) {
            r.setStatus("finished");r.setEndTime(now());
            r.setWinner(roster(r).stream().filter(p->!p.getUser().getId().equals(user.getId())).map(RoomParticipant::getUser).findFirst().orElse(null));rooms.save(r);
        }
        return snapshot(r,user);
    }
    public Map<String,Object> rematch(UUID id,User user) {
        Room r=locked(id,user);
        if(!"finished".equals(r.getStatus())) throw fail(HttpStatus.CONFLICT,"Finish this match first.");
        String code="R"+r.getId().toString().replace("-", "").toUpperCase(Locale.ROOT);
        Room next=rooms.findByJoinCode(code).orElseGet(()->createRoom(user,code));
        next=rooms.lockById(next.getId()).orElseThrow();add(next,user);return snapshot(next,user);
    }
    private Map<String,Object> snapshot(Room r,User user) {
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("id",r.getId());out.put("code",r.getJoinCode());out.put("status",r.getStatus());out.put("round",r.getRoundsPlayed());
        out.put("serverTime",now().toInstant(ZoneOffset.UTC).toString());
        out.put("deadline",r.getEndTime()==null ? null : r.getEndTime().toInstant(ZoneOffset.UTC).toString());
        out.put("host",r.getHost().getUsername());out.put("winner",r.getWinner()==null ? null : r.getWinner().getUsername());
        out.put("players",roster(r).stream().map(p->Map.of("username",p.getUser().getUsername(),"score",p.getScore())).toList());
        var attempts=submissions.findByRoomId(r.getId());
        List<Map<String,Object>> history=new ArrayList<>();
        for(RoomProblem rp:assignments.findByRoomIdOrderBySortOrderAsc(r.getId())) {
            int round=rp.getSortOrder()+1;
            if(round>r.getRoundsPlayed()) continue;
            var p=bank.findBySlug(rp.getSqlSlug()).orElseThrow();
            Map<String,Object> h=new LinkedHashMap<>();h.put("round",round);h.put("title",p.title());
            h.put("winner",attempts.stream().filter(s->p.slug().equals(s.getSqlSlug()) && "accepted".equals(s.getVerdict())).map(s->s.getUser().getUsername()).findFirst().orElse(null));
            if("finished".equals(r.getStatus())) {h.put("explanation",p.explanation());h.put("solution",p.referenceQuery());}
            history.add(h);
        }
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
