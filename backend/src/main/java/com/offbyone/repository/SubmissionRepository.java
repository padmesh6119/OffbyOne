package com.offbyone.repository;

import com.offbyone.model.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    List<Submission> findByUserId(UUID userId);
    List<Submission> findByProblemId(UUID problemId);
    List<Submission> findByRoomId(UUID roomId);
    List<Submission> findByUserIdAndProblemId(UUID userId, UUID problemId);
    List<Submission> findByRoomIdAndProblemIdAndUserId(UUID roomId, UUID problemId, UUID userId);
    List<Submission> findByRoomIdAndProblemIdAndVerdict(UUID roomId, UUID problemId, String verdict);
    List<Submission> findByRoomIdAndSqlSlugAndUserId(UUID roomId, String sqlSlug, UUID userId);
    List<Submission> findByRoomIdAndSqlSlugAndVerdict(UUID roomId, String sqlSlug, String verdict);
}
