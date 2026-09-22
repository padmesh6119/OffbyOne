package com.offbyone.repository;

import com.offbyone.model.RatingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RatingHistoryRepository extends JpaRepository<RatingHistory, UUID> {
    List<RatingHistory> findByUserIdOrderByCreatedAtAsc(UUID userId);
}
