package com.offbyone.controller;

import com.offbyone.config.JwtUtil;
import com.offbyone.model.User;
import com.offbyone.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;
    private final com.offbyone.repository.RoomParticipantRepository participants;
    private final com.offbyone.arena.SqlArenaService arena;

    public AuthController(UserRepository userRepo, JwtUtil jwtUtil, com.offbyone.repository.RoomParticipantRepository participants, com.offbyone.arena.SqlArenaService arena) {
        this.userRepo = userRepo; this.jwtUtil = jwtUtil;
        this.participants = participants; this.arena = arena;
    }

    @GetMapping("/session")
    public ResponseEntity<?> session(@org.springframework.security.core.annotation.AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "Your guest pass has ended."));
        return ResponseEntity.ok(Map.of("username", user.getUsername(), "skillLevel", user.getSkillLevel(),
                "expiresAt", user.getSessionExpiresAt() == null ? "" : user.getSessionExpiresAt().toInstant(java.time.ZoneOffset.UTC).toString()));
    }

    @PatchMapping("/session")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> updateSession(@RequestBody Map<String, String> body, @org.springframework.security.core.annotation.AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        int skill;
        try { skill = Integer.parseInt(body.getOrDefault("skillLevel", "")); }
        catch (NumberFormatException e) { return ResponseEntity.badRequest().body("Choose a skill level from 1 to 10."); }
        if (skill < 1 || skill > 10) return ResponseEntity.badRequest().body("Choose a skill level from 1 to 10.");
        var guest = userRepo.findById(user.getId()).orElseThrow();
        guest.setSkillLevel(skill); userRepo.save(guest);
        return ResponseEntity.ok(Map.of("skillLevel", skill));
    }

    @PostMapping("/logout")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> logout(@org.springframework.security.core.annotation.AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "Your guest pass has already ended."));
        var guest = userRepo.findById(user.getId()).orElseThrow();
        // Consistent lock order when a guest has more than one unfinished match.
        var matches = participants.findByUserId(guest.getId()).stream().map(p -> p.getRoom())
                .filter(r -> "sql-arena".equals(r.getTrack()) && !"finished".equals(r.getStatus()))
                .sorted(java.util.Comparator.comparing(r -> r.getId().toString())).toList();
        for (var match : matches) arena.leave(match.getId(), guest);
        guest.setSessionExpiresAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        guest.setUsername("~expired~" + guest.getEmail());
        userRepo.save(guest);
        return ResponseEntity.ok(Map.of("status", "logged_out"));
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000)
    @org.springframework.transaction.annotation.Transactional
    public void releaseExpiredNames() {
        userRepo.releaseExpiredNames(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
    }

    @PostMapping("/join")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> join(@RequestBody Map<String, String> body) {
        int skill;
        try { skill = Integer.parseInt(body.getOrDefault("skillLevel", "5")); }
        catch (NumberFormatException e) { return ResponseEntity.badRequest().body("Choose a skill level from 1 to 10."); }
        if (skill < 1 || skill > 10) return ResponseEntity.badRequest().body("Choose a skill level from 1 to 10.");
        releaseExpiredNames();
        String raw = body.get("username");
        if (raw == null || raw.isBlank()) return ResponseEntity.badRequest().body("Username required");
        final String username = raw.trim();

        if (!username.matches("[a-zA-Z0-9_-]{2,20}")) return ResponseEntity.badRequest().body("Use 2–20 letters, numbers, underscores or hyphens.");
        if (userRepo.findByUsername(username).isPresent()) return ResponseEntity.status(409).body("That name belongs to an active guest. Choose another name or resume in your original browser.");
        User user = new User();
        user.setUsername(username);
        user.setSkillLevel(skill);
        user.setSessionExpiresAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusNanos(jwtUtil.expiryMs() * 1000000));
        user.setEmail(UUID.randomUUID() + "@offbyone.local");
        user.setPasswordHash(UUID.randomUUID().toString());
        user = userRepo.save(user);

        return ResponseEntity.ok(Map.of("token", jwtUtil.generate(user.getEmail()), "username", user.getUsername(), "skillLevel", skill, "expiresAt", user.getSessionExpiresAt().toInstant(java.time.ZoneOffset.UTC).toString()));
    }
}
