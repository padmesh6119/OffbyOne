package com.offbyone.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class RoomProblemId implements Serializable {
    private UUID room;
    private UUID problem;

    public RoomProblemId() {}
    public RoomProblemId(UUID room, UUID problem) { this.room = room; this.problem = problem; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoomProblemId that)) return false;
        return Objects.equals(room, that.room) && Objects.equals(problem, that.problem);
    }

    @Override
    public int hashCode() { return Objects.hash(room, problem); }
}
