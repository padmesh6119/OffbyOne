package com.offbyone.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class RoomParticipantId implements Serializable {
    private UUID room;
    private UUID user;

    public RoomParticipantId() {}
    public RoomParticipantId(UUID room, UUID user) { this.room = room; this.user = user; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoomParticipantId that)) return false;
        return Objects.equals(room, that.room) && Objects.equals(user, that.user);
    }

    @Override
    public int hashCode() { return Objects.hash(room, user); }
}
