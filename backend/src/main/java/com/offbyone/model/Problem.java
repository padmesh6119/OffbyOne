package com.offbyone.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "problems")
public class Problem {
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false) private String title;
    @Column(unique = true, nullable = false) private String slug;
    @Column(nullable = false, columnDefinition = "TEXT") private String statement;
    @Column(nullable = false) private String difficulty;
    @Column(name = "time_limit_ms") private int timeLimitMs = 2000;
    @Column(name = "memory_limit_mb") private int memoryLimitMb = 256;
    @Column(name = "is_active") private boolean isActive = true;
    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getSlug() { return slug; }
    public void setSlug(String v) { this.slug = v; }
    public String getStatement() { return statement; }
    public void setStatement(String v) { this.statement = v; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String v) { this.difficulty = v; }
    public int getTimeLimitMs() { return timeLimitMs; }
    public void setTimeLimitMs(int v) { this.timeLimitMs = v; }
    public int getMemoryLimitMb() { return memoryLimitMb; }
    public void setMemoryLimitMb(int v) { this.memoryLimitMb = v; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean v) { this.isActive = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
