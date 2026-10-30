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

    public AuthController(UserRepository userRepo, JwtUtil jwtUtil) {
        this.userRepo = userRepo; this.jwtUtil = jwtUtil;
    }

    @PostMapping("/join")
    public ResponseEntity<?> join(@RequestBody Map<String, String> body) {
        String raw = body.get("username");
        if (raw == null || raw.isBlank()) return ResponseEntity.badRequest().body("Username required");
        final String username = raw.trim();

        if (!username.matches("[a-zA-Z0-9_-]{2,20}")) return ResponseEntity.badRequest().body("Use 2–20 letters, numbers, underscores or hyphens.");
        if (userRepo.findByUsername(username).isPresent()) return ResponseEntity.status(409).body("That name is already taken. Choose a new guest name; your existing session stays on its original device.");
        User user = new User();
        user.setUsername(username);
        user.setEmail(UUID.randomUUID() + "@offbyone.local");
        user.setPasswordHash(UUID.randomUUID().toString());
        user = userRepo.save(user);

        return ResponseEntity.ok(Map.of("token", jwtUtil.generate(user.getEmail()), "username", user.getUsername()));
    }
}
