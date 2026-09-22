package com.offbyone.model;

import jakarta.persistence.*;
import java.util.UUID;

/** One assigned problem slot in a room — either a Java {@link Problem} (FK) or a SQL problem
 * (identified by {@code sqlSlug}, since the SQL bank lives in a JSON file, not Postgres). */
@Entity
@Table(name = "room_problems")
public class RoomProblem {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id") private Room room;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id") private Problem problem;
    @Column(name = "sql_slug") private String sqlSlug;
    private int points = 100;
    @Column(name = "sort_order") private int sortOrder;

    public UUID getId() { return id; }
    public Room getRoom() { return room; }
    public void setRoom(Room v) { this.room = v; }
    public Problem getProblem() { return problem; }
    public void setProblem(Problem v) { this.problem = v; }
    public String getSqlSlug() { return sqlSlug; }
    public void setSqlSlug(String v) { this.sqlSlug = v; }
    public boolean isSql() { return sqlSlug != null; }
    public int getPoints() { return points; }
    public void setPoints(int v) { this.points = v; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int v) { this.sortOrder = v; }
}
