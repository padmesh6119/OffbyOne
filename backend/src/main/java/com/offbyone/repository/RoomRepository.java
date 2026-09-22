package com.offbyone.repository;

import com.offbyone.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    Optional<Room> findByJoinCode(String joinCode);
    List<Room> findByStatus(String status);
}
