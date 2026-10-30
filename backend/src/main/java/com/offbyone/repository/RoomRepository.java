package com.offbyone.repository;

import com.offbyone.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from Room r where r.id = :id")
    Optional<Room> lockById(@org.springframework.data.repository.query.Param("id") UUID id);
    Optional<Room> findByJoinCode(String joinCode);
    List<Room> findByStatus(String status);
}
