package com.offbyone.arena;
import com.offbyone.model.User;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.*;
@RestController
@RequestMapping("/api/sql-arena")
public class SqlArenaController {
    private final SqlArenaService arena;
    private final SqlRequestLimit limit;
    public SqlArenaController(SqlArenaService a,SqlRequestLimit l) {arena=a;limit=l;}
    public record Query(int round,String query) {}
    @PostMapping public Map<String,Object> create(@AuthenticationPrincipal User u) {limit.check(u);return arena.create(u);}
    @PostMapping("/join/{code}") public Map<String,Object> join(@PathVariable String code,@AuthenticationPrincipal User u) {limit.check(u);return arena.join(code,u);}
    @GetMapping("/{id}") public Map<String,Object> state(@PathVariable UUID id,@AuthenticationPrincipal User u) {return arena.state(id,u);}
    @PostMapping("/{id}/start") public Map<String,Object> start(@PathVariable UUID id,@AuthenticationPrincipal User u) {return arena.start(id,u);}
    @PostMapping("/{id}/submit") public Map<String,Object> submit(@PathVariable UUID id,@RequestBody Query q,@AuthenticationPrincipal User u) {limit.check(u);return arena.submit(id,u,q.round(),q.query());}
    @PostMapping("/{id}/leave") public Map<String,Object> leave(@PathVariable UUID id,@AuthenticationPrincipal User u) {return arena.leave(id,u);}
    @PostMapping("/{id}/rematch") public Map<String,Object> rematch(@PathVariable UUID id,@AuthenticationPrincipal User u) {limit.check(u);return arena.rematch(id,u);}
}
