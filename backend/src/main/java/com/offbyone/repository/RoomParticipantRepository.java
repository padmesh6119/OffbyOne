package com.offbyone.repository;

import com.offbyone.model.RoomParticipant;
import com.offbyone.model.RoomParticipantId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomParticipantRepository extends JpaRepository<RoomParticipant, RoomParticipantId> {
    Optional<RoomParticipant> findByRoomIdAndUserId(UUID roomId, UUID userId);
    List<RoomParticipant> findByRoomIdOrderByScoreDesc(UUID roomId);
}
