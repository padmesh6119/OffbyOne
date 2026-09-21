package com.offbyone.repository;

import com.offbyone.model.RoomProblem;
import com.offbyone.model.RoomProblemId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomProblemRepository extends JpaRepository<RoomProblem, RoomProblemId> {
    Optional<RoomProblem> findByRoomIdAndProblemId(UUID roomId, UUID problemId);
    List<RoomProblem> findByRoomIdOrderBySortOrderAsc(UUID roomId);
}
