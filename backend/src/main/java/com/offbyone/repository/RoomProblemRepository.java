package com.offbyone.repository;

import com.offbyone.model.RoomProblem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomProblemRepository extends JpaRepository<RoomProblem, UUID> {
    Optional<RoomProblem> findByRoomIdAndProblemId(UUID roomId, UUID problemId);
    Optional<RoomProblem> findByRoomIdAndSqlSlug(UUID roomId, String sqlSlug);
    List<RoomProblem> findByRoomIdOrderBySortOrderAsc(UUID roomId);
}
