package com.codecompete.controller;

import com.codecompete.config.JwtUtil;
import com.codecompete.model.User;
import com.codecompete.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String username = body.get("username");
        String password = body.get("password");

        if (userRepo.existsByEmail(email)) return ResponseEntity.badRequest().body("Email taken");
        if (userRepo.existsByUsername(username)) return ResponseEntity.badRequest().body("Username taken");

        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        userRepo.save(user);

        return ResponseEntity.ok(Map.of("token", jwtUtil.generate(email)));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        return userRepo.findByEmail(body.get("email"))
                .filter(u -> encoder.matches(body.get("password"), u.getPasswordHash()))
                .map(u -> ResponseEntity.ok(Map.of("token", jwtUtil.generate(u.getEmail()))))
                .orElse(ResponseEntity.status(401).build());
    }
}
