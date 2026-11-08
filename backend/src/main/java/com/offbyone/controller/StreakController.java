package com.offbyone.controller;

import com.offbyone.model.Problem;
import com.offbyone.model.Submission;
import com.offbyone.model.User;
import com.offbyone.repository.ProblemRepository;
import com.offbyone.repository.SubmissionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class StreakController {
    private final SubmissionRepository submissionRepo;
    private final com.offbyone.sql.SqlProblemBank bank;

    public StreakController(SubmissionRepository submissionRepo, com.offbyone.sql.SqlProblemBank bank) {
        this.submissionRepo = submissionRepo; this.bank = bank;
    }

    /** Current/longest streak of distinct days with at least one accepted submission. */
    @GetMapping("/streak")
    @Transactional(readOnly = true)
    public Map<String, Object> streak(@AuthenticationPrincipal User user) {
        List<LocalDate> days = submissionRepo.findByUserId(user.getId()).stream()
                .filter(s -> "sql".equals(s.getLanguage()) && "accepted".equals(s.getVerdict()))
                .map(s -> s.getSubmittedAt().toLocalDate())
                .distinct().sorted(Comparator.reverseOrder()).toList();

        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        boolean solvedToday = !days.isEmpty() && days.get(0).equals(today);

        int current = 0;
        LocalDate cursor = solvedToday || days.isEmpty() ? today : today.minusDays(1);
        for (LocalDate d : days) {
            if (d.equals(cursor)) { current++; cursor = cursor.minusDays(1); }
            else if (d.isBefore(cursor)) break;
        }

        List<LocalDate> asc = new ArrayList<>(days);
        Collections.reverse(asc);
        int longest = 0, run = 0;
        LocalDate prev = null;
        for (LocalDate d : asc) {
            run = (prev != null && prev.plusDays(1).equals(d)) ? run + 1 : 1;
            longest = Math.max(longest, run);
            prev = d;
        }

        return Map.of("currentStreak", current, "longestStreak", longest, "solvedToday", solvedToday);
    }

    /** Same problem for everyone each day — deterministic pick by date, solved via the normal solo submit flow. */
    @GetMapping("/daily-challenge")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> dailyChallenge() {
        var all = bank.findAll().stream().filter(p -> "easy".equals(p.difficulty()))
                .sorted(Comparator.comparing(com.offbyone.sql.SqlProblem::slug)).toList();
        var p = all.get((int) Math.floorMod(LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay(), all.size()));
        return ResponseEntity.ok(Map.of("slug", p.slug(), "title", p.title(), "difficulty", p.difficulty()));
    }
}
