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
        String username = body.get("username");
        if (username == null || username.isBlank()) return ResponseEntity.badRequest().body("Username required");
        username = username.trim();

        User user = userRepo.findByUsername(username).orElseGet(() -> {
            User u = new User();
            u.setUsername(username);
            u.setEmail(username + "@offbyone.local");
            u.setPasswordHash(UUID.randomUUID().toString());
            return userRepo.save(u);
        });

        return ResponseEntity.ok(Map.of("token", jwtUtil.generate(user.getEmail()), "username", user.getUsername()));
    }
}
