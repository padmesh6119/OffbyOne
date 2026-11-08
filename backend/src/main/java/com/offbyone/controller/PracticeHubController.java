package com.offbyone.controller;

import com.offbyone.model.Submission;
import com.offbyone.model.User;
import com.offbyone.repository.SubmissionRepository;
import com.offbyone.sql.SqlProblem;
import com.offbyone.sql.SqlProblemBank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/practice-hub")
public class PracticeHubController {
    private final SubmissionRepository submissions;
    private final SqlProblemBank bank;

    public PracticeHubController(SubmissionRepository submissions, SqlProblemBank bank) {
        this.submissions = submissions;
        this.bank = bank;
    }

    static int points(SqlProblem p) {
        return switch (p.difficulty()) { case "hard" -> 350; case "medium" -> 200; default -> 100; };
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> summary(@AuthenticationPrincipal User user) {
        var attempts = submissions.findByUserId(user.getId()).stream()
                .filter(s -> "sql".equals(s.getLanguage()) && bank.findBySlug(s.getSqlSlug()).isPresent())
                .sorted(Comparator.comparing(Submission::getSubmittedAt)).toList();
        Set<String> solved = new HashSet<>();
        Set<String> attempted = new HashSet<>();
        int firstTry = 0;
        for (var s : attempts) {
            if (attempted.add(s.getSqlSlug()) && "accepted".equals(s.getVerdict())) firstTry++;
            if ("accepted".equals(s.getVerdict())) solved.add(s.getSqlSlug());
        }
        var problems = bank.findAll();
        int xp = problems.stream().filter(p -> solved.contains(p.slug())).mapToInt(PracticeHubController::points).sum();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var daily = dailyProblem(today);
        boolean dailyDone = attempts.stream().anyMatch(s -> "accepted".equals(s.getVerdict())
                && daily.slug().equals(s.getSqlSlug()) && today.equals(s.getSubmittedAt().toLocalDate()));
        long joins = problems.stream().filter(p -> solved.contains(p.slug()) && p.pattern().contains("join")).count();
        var badges = List.of(
                badge("first", "First blood", "Solve your first SQL challenge.", !solved.isEmpty()),
                badge("five", "Finding your flow", "Solve five different challenges.", solved.size() >= 5),
                badge("ten", "Double digits", "Solve ten different challenges.", solved.size() >= 10),
                badge("precise", "One shot", "Solve a challenge on your first submission.", firstTry > 0),
                badge("joins", "Better together", "Solve three challenges involving joins.", joins >= 3),
                badge("daily", "Daily discipline", "Clear today's daily challenge.", dailyDone));
        return Map.of("xp", xp, "level", 1 + xp / 500, "levelProgress", xp % 500,
                "solved", solved, "attempts", attempts.size(), "firstTry", firstTry, "badges", badges,
                "daily", Map.of("slug", daily.slug(), "title", daily.title(), "difficulty", daily.difficulty(),
                        "completed", dailyDone, "date", today.toString(),
                        "resetsAt", today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString()));
    }

    SqlProblem dailyProblem(LocalDate day) {
        // Beginner accessible, deterministic for everyone, changes at midnight UTC.
        var pool = bank.findAll().stream().filter(p -> "easy".equals(p.difficulty()))
                .sorted(Comparator.comparing(SqlProblem::slug)).toList();
        return pool.get((int) Math.floorMod(day.toEpochDay(), pool.size()));
    }

    private Map<String, Object> badge(String id, String title, String description, boolean unlocked) {
        return Map.of("id", id, "title", title, "description", description, "unlocked", unlocked);
    }

    @GetMapping("/leaderboard")
    @Transactional(readOnly = true)
    public Map<String, Object> leaderboard(@AuthenticationPrincipal User viewer) {
        Map<UUID, Map<String, Object>> players = new HashMap<>();
        for (var row : submissions.activeGuestSolves(LocalDateTime.now(ZoneOffset.UTC))) {
            var problem = bank.findBySlug((String) row[2]);
            if (problem.isEmpty()) continue;
            UUID id = (UUID) row[0];
            var player = players.computeIfAbsent(id, key -> new HashMap<>(Map.of("username", row[1], "xp", 0, "solved", 0, "you", id.equals(viewer.getId()))));
            player.put("xp", (Integer) player.get("xp") + points(problem.get()));
            player.put("solved", (Integer) player.get("solved") + 1);
        }
        var ranked = players.values().stream().sorted(Comparator.<Map<String, Object>>comparingInt(p -> (Integer) p.get("xp"))
                .reversed().thenComparing(p -> (String) p.get("username"))).toList();
        int rank = 0, previous = -1;
        Object yourRank = 0;
        for (int i = 0; i < ranked.size(); i++) {
            var p = ranked.get(i);
            int xp = (Integer) p.get("xp");
            if (xp != previous) rank = i + 1;
            previous = xp;
            p.put("rank", rank);
            if (Boolean.TRUE.equals(p.get("you"))) yourRank = rank;
        }
        return Map.of("players", ranked.stream().limit(50).toList(), "yourRank", yourRank,
                "totalPlayers", ranked.size(), "generatedAt", Instant.now().toString());
    }
}
