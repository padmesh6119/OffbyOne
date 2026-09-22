package com.offbyone.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "room_participants")
@IdClass(RoomParticipantId.class)
public class RoomParticipant {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id") private Room room;
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") private User user;
    private int score = 0;
    private Integer rank;
    @Column(name = "joined_at") private LocalDateTime joinedAt = LocalDateTime.now();
    @Column(name = "solved_count") private int solvedCount = 0;
    @Column(name = "last_solve_at") private LocalDateTime lastSolveAt;

    public Room getRoom() { return room; }
    public void setRoom(Room v) { this.room = v; }
    public User getUser() { return user; }
    public void setUser(User v) { this.user = v; }
    public int getScore() { return score; }
    public void setScore(int v) { this.score = v; }
    public Integer getRank() { return rank; }
    public void setRank(Integer v) { this.rank = v; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public int getSolvedCount() { return solvedCount; }
    public void setSolvedCount(int v) { this.solvedCount = v; }
    public LocalDateTime getLastSolveAt() { return lastSolveAt; }
    public void setLastSolveAt(LocalDateTime v) { this.lastSolveAt = v; }
}
