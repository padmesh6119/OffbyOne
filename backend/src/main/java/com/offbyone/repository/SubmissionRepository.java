package com.offbyone.repository;

import com.offbyone.model.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    @org.springframework.data.jpa.repository.Query("select distinct s.user.id, s.user.username, s.sqlSlug from Submission s where s.language = 'sql' and s.verdict = 'accepted' and s.user.sessionExpiresAt > :now")
    List<Object[]> activeGuestSolves(@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);
    @org.springframework.data.jpa.repository.Query("select s.user.id, s.user.username, s.verdict from Submission s where s.language = 'sql' and s.room is null and s.sqlSlug = :slug and s.submittedAt >= :start and s.submittedAt < :end and s.user.sessionExpiresAt > :now order by s.submittedAt, s.id")
    List<Object[]> dailyAttempts(@org.springframework.data.repository.query.Param("slug") String slug, @org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start, @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end, @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);
    List<Submission> findByUserId(UUID userId);
    List<Submission> findByProblemId(UUID problemId);
    List<Submission> findByRoomId(UUID roomId);
    List<Submission> findByUserIdAndProblemId(UUID userId, UUID problemId);
    List<Submission> findByRoomIdAndProblemIdAndUserId(UUID roomId, UUID problemId, UUID userId);
    List<Submission> findByRoomIdAndProblemIdAndVerdict(UUID roomId, UUID problemId, String verdict);
    List<Submission> findByRoomIdAndSqlSlugAndUserId(UUID roomId, String sqlSlug, UUID userId);
    List<Submission> findByRoomIdAndSqlSlugAndVerdict(UUID roomId, String sqlSlug, String verdict);
}
