package com.offbyone.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue
    private UUID id;
    @Column(unique = true, nullable = false) private String username;
    @Column(unique = true, nullable = false) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    private int rating = 1200;
    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String v) { this.username = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String v) { this.passwordHash = v; }
    public int getRating() { return rating; }
    public void setRating(int v) { this.rating = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
