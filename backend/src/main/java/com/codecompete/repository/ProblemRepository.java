package com.codecompete.repository;

import com.codecompete.model.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProblemRepository extends JpaRepository<Problem, UUID> {
    Optional<Problem> findBySlug(String slug);
    List<Problem> findByIsActiveTrue();
    List<Problem> findByDifficultyAndIsActiveTrue(String difficulty);
}
