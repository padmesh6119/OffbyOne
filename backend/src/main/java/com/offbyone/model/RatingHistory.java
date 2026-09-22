package com.offbyone.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** One Elo change event, written whenever a tournament room finishes — powers the Profile rating graph. */
@Entity
@Table(name = "rating_history")
public class RatingHistory {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private User user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "room_id") private Room room;
    private int rating;
    private int delta;
    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User v) { this.user = v; }
    public Room getRoom() { return room; }
    public void setRoom(Room v) { this.room = v; }
    public int getRating() { return rating; }
    public void setRating(int v) { this.rating = v; }
    public int getDelta() { return delta; }
    public void setDelta(int v) { this.delta = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
