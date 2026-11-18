package com.offbyone.sprint;
import com.offbyone.model.User;
import com.offbyone.arena.SqlRequestLimit;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.*;
@RestController
@RequestMapping("/api/sql-sprints")
public class SqlSprintController {
    private final SqlSprintService service; private final SqlRequestLimit limit;
    public SqlSprintController(SqlSprintService service,SqlRequestLimit limit) {this.service=service;this.limit=limit;}
    public record Options(int minutes,String difficulty) {}
    public record Move(int position,String query) {}
    @GetMapping public List<Map<String,Object>> history(@AuthenticationPrincipal User u) {return service.history(u);}
    @PostMapping public Map<String,Object> create(@RequestBody Options o,@AuthenticationPrincipal User u) {limit.check(u);return service.create(u,o.minutes(),o.difficulty());}
    @GetMapping("/{id}") public Map<String,Object> state(@PathVariable UUID id,@AuthenticationPrincipal User u) {return service.state(id,u);}
    @PostMapping("/{id}/submit") public Map<String,Object> submit(@PathVariable UUID id,@RequestBody Move m,@AuthenticationPrincipal User u) {limit.check(u);return service.submit(id,u,m.position(),m.query());}
    @PostMapping("/{id}/skip") public Map<String,Object> skip(@PathVariable UUID id,@RequestBody Move m,@AuthenticationPrincipal User u) {limit.check(u);return service.skip(id,u,m.position());}
    @PostMapping("/{id}/finish") public Map<String,Object> finish(@PathVariable UUID id,@AuthenticationPrincipal User u) {return service.finish(id,u);}
}
