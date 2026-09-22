package com.offbyone.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rooms")
public class Room {
    @Id @GeneratedValue private UUID id;
    @Column(name = "join_code", unique = true, nullable = false) private String joinCode;
    @Column(nullable = false) private String name;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id") private User host;
    @Column(nullable = false) private String status = "waiting";
    @Column(name = "start_time") private LocalDateTime startTime;
    @Column(name = "end_time") private LocalDateTime endTime;
    @Column(name = "problem_count") private int problemCount = 5;
    @Column(nullable = false) private String track = "java"; // "java" | "mixed" | "sql"
    @Column(name = "duration_minutes") private int durationMinutes = 30;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_problem_id") private Problem currentProblem;
    @Column(name = "current_sql_slug") private String currentSqlSlug;
    @Column(name = "rounds_played") private int roundsPlayed = 0;
    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();

    public UUID getId() { return id; }
    public String getJoinCode() { return joinCode; }
    public void setJoinCode(String v) { this.joinCode = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public User getHost() { return host; }
    public void setHost(User v) { this.host = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime v) { this.startTime = v; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime v) { this.endTime = v; }
    public int getProblemCount() { return problemCount; }
    public void setProblemCount(int v) { this.problemCount = v; }
    public String getTrack() { return track; }
    public void setTrack(String v) { this.track = v; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int v) { this.durationMinutes = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Problem getCurrentProblem() { return currentProblem; }
    public void setCurrentProblem(Problem v) { this.currentProblem = v; }
    public String getCurrentSqlSlug() { return currentSqlSlug; }
    public void setCurrentSqlSlug(String v) { this.currentSqlSlug = v; }
    public int getRoundsPlayed() { return roundsPlayed; }
    public void setRoundsPlayed(int v) { this.roundsPlayed = v; }
}
