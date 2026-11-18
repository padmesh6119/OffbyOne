package com.offbyone.sprint;

import com.offbyone.model.*;
import com.offbyone.repository.*;
import com.offbyone.sql.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

/** A solo timed run persisted in rooms. The database owns its clock, order and score. */
@Service
@Transactional
public class SqlSprintService {
    private final RoomRepository rooms;
    private final RoomProblemRepository assignments;
    private final SubmissionRepository submissions;
    private final UserRepository users;
    private final SqlProblemBank bank;
    private final SqlJudge judge;
    public SqlSprintService(RoomRepository rooms, RoomProblemRepository assignments, SubmissionRepository submissions,
                            UserRepository users, SqlProblemBank bank, SqlJudge judge) {
        this.rooms=rooms; this.assignments=assignments; this.submissions=submissions;
        this.users=users; this.bank=bank; this.judge=judge;
    }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    private ResponseStatusException fail(HttpStatus status,String text) { return new ResponseStatusException(status,text); }
    private Room owned(UUID id,User user) {
        Room r=rooms.lockById(id).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"Sprint not found."));
        if (!"sql-sprint".equals(r.getTrack()) || !r.getHost().getId().equals(user.getId()))
            throw fail(HttpStatus.NOT_FOUND,"This sprint belongs to a different guest pass.");
        expire(r); return r;
    }
    private void expire(Room r) {
        if ("active".equals(r.getStatus()) && !now().isBefore(r.getEndTime())) r.setStatus("finished");
    }
    public Map<String,Object> create(User user,int minutes,String difficulty) {
        if (!List.of(5,10,15).contains(minutes) || difficulty==null || !List.of("easy","medium","hard").contains(difficulty))
            throw fail(HttpStatus.BAD_REQUEST,"Choose 5, 10 or 15 minutes and an easy, medium or hard level.");
        // Serialize concurrent starts from multiple tabs for the same guest.
        users.lockById(user.getId()).orElseThrow();
        for (Room existing:rooms.findByHostIdAndTrackOrderByCreatedAtDesc(user.getId(),"sql-sprint")) {
            if (!"active".equals(existing.getStatus())) continue;
            Room locked=owned(existing.getId(),user);
            if ("active".equals(locked.getStatus())) return snapshot(locked);
        }
        var pool=new ArrayList<>(bank.findAll().stream().filter(p->difficulty.equals(p.difficulty())).toList());
        Collections.shuffle(pool);
        var selected=pool.stream().limit(20).toList();
        if (selected.isEmpty()) throw fail(HttpStatus.SERVICE_UNAVAILABLE,"No challenges at this level yet.");
        Room r=new Room();r.setHost(user);r.setName(difficulty);r.setTrack("sql-sprint");
        r.setJoinCode("S"+UUID.randomUUID().toString().replace("-",""));r.setDurationMinutes(minutes);
        r.setProblemCount(selected.size());r.setRoundsPlayed(0);r.setCurrentSqlSlug(selected.get(0).slug());
        r.setStatus("active");r.setStartTime(now());r.setEndTime(r.getStartTime().plusMinutes(minutes));rooms.save(r);
        for (int i=0;i<selected.size();i++) {
            RoomProblem rp=new RoomProblem();rp.setRoom(r);rp.setSqlSlug(selected.get(i).slug());rp.setSortOrder(i);rp.setPoints(100);assignments.save(rp);
        }
        return snapshot(r);
    }
    public Map<String,Object> state(UUID id,User user) { return snapshot(owned(id,user)); }
    public Map<String,Object> finish(UUID id,User user) {
        Room r=owned(id,user);
        if ("active".equals(r.getStatus())) {r.setStatus("finished");r.setEndTime(now());}
        return snapshot(r);
    }
    private void advance(Room r) {
        r.setRoundsPlayed(r.getRoundsPlayed()+1);
        if (r.getRoundsPlayed()>=r.getProblemCount()) {r.setStatus("finished");r.setEndTime(now());}
        else r.setCurrentSqlSlug(assignments.findByRoomIdOrderBySortOrderAsc(r.getId()).get(r.getRoundsPlayed()).getSqlSlug());
    }
    private void requireCurrent(Room r,int position) {
        if (!"active".equals(r.getStatus()) || position!=r.getRoundsPlayed())
            throw fail(HttpStatus.CONFLICT,"This challenge has ended. Refresh the sprint to continue.");
    }
    public Map<String,Object> skip(UUID id,User user,int position) {
        Room r=owned(id,user);requireCurrent(r,position);advance(r);return snapshot(r);
    }
    public Map<String,Object> submit(UUID id,User user,int position,String query) {
        if (query==null || query.isBlank() || query.length()>16000) throw fail(HttpStatus.BAD_REQUEST,"Write a query of 1–16,000 characters.");
        Room r=owned(id,user);requireCurrent(r,position);
        SqlProblem p=bank.findBySlug(r.getCurrentSqlSlug()).orElseThrow();
        var verdict=judge.judge(p,query);
        if ("busy".equals(verdict.status())) throw fail(HttpStatus.TOO_MANY_REQUESTS,verdict.message());
        // Judging may cross the deadline; a late result must never add points.
        expire(r);
        if (!"active".equals(r.getStatus())) return Map.of("result",Map.of("status","time_up","message","Time is up. This query was not scored."),"sprint",snapshot(r));
        Submission s=new Submission();s.setUser(user);s.setRoom(r);s.setSqlSlug(p.slug());s.setLanguage("sql");s.setCode(query);s.setVerdict(verdict.status());submissions.save(s);
        if ("accepted".equals(verdict.status())) advance(r);
        return Map.of("result",verdict,"sprint",snapshot(r));
    }
    private Map<String,Object> stats(Room r) {
        var attempts=submissions.findByRoomId(r.getId()).stream().sorted(Comparator.comparing(Submission::getSubmittedAt)).toList();
        Set<String> tried=new HashSet<>(),solved=new HashSet<>();int firstTry=0;
        for (var s:attempts) {
            boolean first=tried.add(s.getSqlSlug());
            if ("accepted".equals(s.getVerdict()) && solved.add(s.getSqlSlug()) && first) firstTry++;
        }
        Map<String,Object> out=new LinkedHashMap<>();out.put("id",r.getId());out.put("difficulty",r.getName());out.put("minutes",r.getDurationMinutes());
        out.put("status",!now().isBefore(r.getEndTime()) ? "finished" : r.getStatus());
        out.put("score",solved.size()*100+firstTry*50);out.put("solved",solved.size());out.put("firstTry",firstTry);out.put("attempts",attempts.size());
        out.put("accuracy",attempts.isEmpty() ? 0 : Math.round(100.0*solved.size()/attempts.size()));
        out.put("startedAt",r.getStartTime().toInstant(ZoneOffset.UTC).toString());
        out.put("secondsUsed",Math.max(0,Duration.between(r.getStartTime(),"active".equals(out.get("status")) ? now() : r.getEndTime()).getSeconds()));
        return out;
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> history(User user) {
        return rooms.findByHostIdAndTrackOrderByCreatedAtDesc(user.getId(),"sql-sprint").stream().limit(30).map(this::stats).toList();
    }
    private Map<String,Object> snapshot(Room r) {
        var out=stats(r);out.put("position",r.getRoundsPlayed());out.put("total",r.getProblemCount());
        out.put("serverTime",now().toInstant(ZoneOffset.UTC).toString());out.put("deadline",r.getEndTime().toInstant(ZoneOffset.UTC).toString());
        if ("active".equals(r.getStatus())) {
            var p=bank.findBySlug(r.getCurrentSqlSlug()).orElseThrow();
            out.put("problem",Map.of("slug",p.slug(),"title",p.title(),"difficulty",p.difficulty(),"pattern",p.pattern(),"task",p.task(),"schema",p.schema(),"tables",judge.previewTables(p)));
        } else {
            var attempts=submissions.findByRoomId(r.getId());
            out.put("review",assignments.findByRoomIdOrderBySortOrderAsc(r.getId()).stream()
                .filter(a->a.getSortOrder()<=r.getRoundsPlayed()).map(a->{
                    var p=bank.findBySlug(a.getSqlSlug()).orElseThrow();
                    boolean solved=attempts.stream().anyMatch(s->p.slug().equals(s.getSqlSlug()) && "accepted".equals(s.getVerdict()));
                    return Map.of("slug",p.slug(),"title",p.title(),"solved",solved,"solution",p.referenceQuery(),"explanation",p.explanation());
                }).toList());
        }
        return out;
    }
}
