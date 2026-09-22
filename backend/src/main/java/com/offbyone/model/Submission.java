package com.offbyone.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "submissions")
public class Submission {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private User user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "problem_id") private Problem problem;
    @Column(name = "sql_slug") private String sqlSlug;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "room_id") private Room room;
    @Column(nullable = false) private String language;
    @Column(nullable = false, columnDefinition = "TEXT") private String code;
    private String verdict = "pending";
    @Column(name = "runtime_ms") private Integer runtimeMs;
    @Column(name = "memory_kb") private Integer memoryKb;
    @Column(name = "submitted_at") private LocalDateTime submittedAt = LocalDateTime.now();

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User v) { this.user = v; }
    public Problem getProblem() { return problem; }
    public void setProblem(Problem v) { this.problem = v; }
    public String getSqlSlug() { return sqlSlug; }
    public void setSqlSlug(String v) { this.sqlSlug = v; }
    public Room getRoom() { return room; }
    public void setRoom(Room v) { this.room = v; }
    public String getLanguage() { return language; }
    public void setLanguage(String v) { this.language = v; }
    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public String getVerdict() { return verdict; }
    public void setVerdict(String v) { this.verdict = v; }
    public Integer getRuntimeMs() { return runtimeMs; }
    public void setRuntimeMs(Integer v) { this.runtimeMs = v; }
    public Integer getMemoryKb() { return memoryKb; }
    public void setMemoryKb(Integer v) { this.memoryKb = v; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
}
