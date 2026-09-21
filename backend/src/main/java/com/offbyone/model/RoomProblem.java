package com.offbyone.model;

import jakarta.persistence.*;

@Entity
@Table(name = "room_problems")
@IdClass(RoomProblemId.class)
public class RoomProblem {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id") private Room room;
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id") private Problem problem;
    private int points = 100;
    @Column(name = "sort_order") private int sortOrder;

    public Room getRoom() { return room; }
    public void setRoom(Room v) { this.room = v; }
    public Problem getProblem() { return problem; }
    public void setProblem(Problem v) { this.problem = v; }
    public int getPoints() { return points; }
    public void setPoints(int v) { this.points = v; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int v) { this.sortOrder = v; }
}
