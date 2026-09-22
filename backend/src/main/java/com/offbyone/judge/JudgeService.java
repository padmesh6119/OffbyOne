package com.offbyone.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offbyone.duel.DuelRoundService;
import com.offbyone.model.Problem;
import com.offbyone.model.Room;
import com.offbyone.model.RoomParticipant;
import com.offbyone.model.Submission;
import com.offbyone.model.TestCase;
import com.offbyone.repository.RoomParticipantRepository;
import com.offbyone.repository.RoomProblemRepository;
import com.offbyone.repository.SubmissionRepository;
import com.offbyone.repository.TestCaseRepository;
import com.offbyone.sql.SqlJudge;
import com.offbyone.sql.SqlProblem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JudgeService {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final String JUDGE0_URL = "https://judge0-ce.p.rapidapi.com/submissions?base64_encoded=false&wait=true";

    private static final Map<String, Integer> LANG_ID = Map.of(
        "java", 62, "python", 71, "cpp", 54
    );

    private final SubmissionRepository submissionRepo;
    private final TestCaseRepository testCaseRepo;
    private final RoomParticipantRepository participantRepo;
    private final RoomProblemRepository roomProblemRepo;
    private final SimpMessagingTemplate ws;
    private final ObjectMapper json;
    private final DuelRoundService duelRoundService;
    private final SqlJudge sqlJudge;

    @Value("${judge0.api.key:}")
    private String apiKey;

    public JudgeService(SubmissionRepository submissionRepo, TestCaseRepository testCaseRepo,
                         RoomParticipantRepository participantRepo, RoomProblemRepository roomProblemRepo,
                         SimpMessagingTemplate ws, ObjectMapper json, DuelRoundService duelRoundService, SqlJudge sqlJudge) {
        this.submissionRepo = submissionRepo; this.testCaseRepo = testCaseRepo;
        this.participantRepo = participantRepo; this.roomProblemRepo = roomProblemRepo;
        this.ws = ws; this.json = json; this.duelRoundService = duelRoundService; this.sqlJudge = sqlJudge;
    }

    /** SQL submissions judge synchronously (in-memory SQLite, no external API) then run the same
     * room scoring path as Java submissions — see awardPoints()/applyWrongAnswerPenalty(). */
    @Async
    public void judgeSql(Submission submission, SqlProblem sqlProblem) {
        SqlJudge.Verdict v = sqlJudge.judge(sqlProblem, submission.getCode());
        submission.setVerdict(v.status());
        submissionRepo.save(submission);

        Map<String, Object> verdictPayload = new java.util.LinkedHashMap<>();
        verdictPayload.put("verdict", v.status()); verdictPayload.put("message", v.message());
        if (v.actualColumns() != null) verdictPayload.put("actual", Map.of("columns", v.actualColumns(), "rows", v.actualRows()));
        if (v.expectedColumns() != null) verdictPayload.put("expected", Map.of("columns", v.expectedColumns(), "rows", v.expectedRows()));
        ws.convertAndSend("/topic/submission/" + submission.getId(), verdictPayload);

        if (submission.getRoom() != null) {
            boolean firstBlood = false;
            if ("accepted".equals(v.status())) firstBlood = awardPoints(submission);
            else if ("wrong_answer".equals(v.status())) applyWrongAnswerPenalty(submission);
            Map<String, Object> roomPayload = new java.util.LinkedHashMap<>();
            roomPayload.put("submissionId", submission.getId()); roomPayload.put("userId", submission.getUser().getId());
            roomPayload.put("username", submission.getUser().getUsername());
            roomPayload.put("sqlSlug", submission.getSqlSlug()); roomPayload.put("verdict", v.status());
            if (firstBlood) roomPayload.put("firstBlood", true);
            ws.convertAndSend("/topic/room/" + submission.getRoom().getId() + "/submission", roomPayload);
        }
    }

    @Async
    public void judge(Submission submission) {
        Problem problem = submission.getProblem();
        List<TestCase> testCases = testCaseRepo.findByProblemId(problem.getId());
        String verdict = "accepted";
        int totalRuntime = 0;
        String message = "";
        Map<String, Object> sampleFailed = null;

        int testIndex = 0;
        for (TestCase tc : testCases) {
            testIndex++;
            ws.convertAndSend("/topic/submission/" + submission.getId() + "/progress",
                Map.of("current", testIndex, "total", testCases.size()));
            RunResult result = run(submission.getCode(), submission.getLanguage(), tc.getInput(), problem.getTimeLimitMs(), problem.getMemoryLimitMb());
            totalRuntime = Math.max(totalRuntime, result.runtimeMs());
            if (result.verdict().equals("ce")) { verdict = "ce"; message = result.message(); break; }
            if (result.verdict().equals("tle")) {
                verdict = "tle";
                message = "Exceeded " + problem.getTimeLimitMs() + "ms on test case " + testIndex;
                break;
            }
            if (result.verdict().equals("mle")) { verdict = "mle"; break; }
            if (result.verdict().equals("re")) { verdict = "re"; message = result.message(); break; }
            if (!result.stdout().trim().equals(tc.getExpectedOutput().trim())) {
                verdict = "wrong_answer";
                if (tc.isSample()) {
                    sampleFailed = Map.of("input", tc.getInput(), "expected", tc.getExpectedOutput(), "got", result.stdout());
                } else {
                    message = "Test case " + testIndex + " failed";
                }
                break;
            }
        }

        submission.setVerdict(verdict); submission.setRuntimeMs(totalRuntime);
        submissionRepo.save(submission);
        Map<String, Object> verdictPayload = new java.util.LinkedHashMap<>();
        verdictPayload.put("verdict", verdict); verdictPayload.put("runtimeMs", totalRuntime); verdictPayload.put("message", message);
        verdictPayload.put("testIndex", testIndex); verdictPayload.put("totalTests", testCases.size());
        if (sampleFailed != null) verdictPayload.put("sampleFailed", sampleFailed);
        ws.convertAndSend("/topic/submission/" + submission.getId(), verdictPayload);

        if (submission.getRoom() != null) {
            boolean firstBlood = false;
            if ("accepted".equals(verdict)) firstBlood = awardPoints(submission);
            else if ("wrong_answer".equals(verdict)) applyWrongAnswerPenalty(submission);
            Map<String, Object> roomPayload = new java.util.LinkedHashMap<>();
            roomPayload.put("submissionId", submission.getId()); roomPayload.put("userId", submission.getUser().getId());
            roomPayload.put("username", submission.getUser().getUsername());
            roomPayload.put("problemId", submission.getProblem().getId()); roomPayload.put("verdict", verdict);
            if (firstBlood) roomPayload.put("firstBlood", true);
            ws.convertAndSend("/topic/room/" + submission.getRoom().getId() + "/submission", roomPayload);
        }
    }

    /** @return true if this submission was the first accepted solve for its problem in this room. */
    private boolean awardPoints(Submission submission) {
        UUID roomId = submission.getRoom().getId(), userId = submission.getUser().getId();
        boolean isSql = submission.getProblem() == null;
        UUID problemId = isSql ? null : submission.getProblem().getId();
        String sqlSlug = submission.getSqlSlug();

        boolean alreadySolved = (isSql
                ? submissionRepo.findByRoomIdAndSqlSlugAndUserId(roomId, sqlSlug, userId)
                : submissionRepo.findByRoomIdAndProblemIdAndUserId(roomId, problemId, userId)).stream()
            .anyMatch(s -> "accepted".equals(s.getVerdict()) && !s.getId().equals(submission.getId()));
        if (alreadySolved) return false;

        boolean firstBlood = (isSql
                ? submissionRepo.findByRoomIdAndSqlSlugAndVerdict(roomId, sqlSlug, "accepted")
                : submissionRepo.findByRoomIdAndProblemIdAndVerdict(roomId, problemId, "accepted")).stream()
            .noneMatch(s -> !s.getId().equals(submission.getId()));

        Room room = submission.getRoom();
        if (room.getProblemCount() == 1) {
            if (firstBlood) handleDuelRoundWin(submission, room);
            return firstBlood; // duel mode: only the round winner scores, no decay/bonus scoring below
        }

        int basePoints = (isSql
                ? roomProblemRepo.findByRoomIdAndSqlSlug(roomId, sqlSlug)
                : roomProblemRepo.findByRoomIdAndProblemId(roomId, problemId)).map(rp -> rp.getPoints()).orElse(100);
        int points = basePoints;
        if (room.getStartTime() != null && room.getEndTime() != null) {
            long totalMs = java.time.Duration.between(room.getStartTime(), room.getEndTime()).toMillis();
            if (totalMs > 0) {
                long elapsedMs = java.time.Duration.between(room.getStartTime(), java.time.LocalDateTime.now()).toMillis();
                double ratio = Math.min(1.0, Math.max(0.0, (double) elapsedMs / totalMs));
                points = (int) Math.floor(basePoints * (1 - ratio * 0.5));
            }
        }
        if (firstBlood) points += 50;

        RoomParticipant participant = participantRepo.findByRoomIdAndUserId(roomId, userId).orElseGet(() -> {
            RoomParticipant p = new RoomParticipant();
            p.setRoom(room); p.setUser(submission.getUser());
            return p;
        });

        // Catch-up: trailing player earns a bonus proportional to the gap with the current
        // leader, capped so it can't outweigh actually solving problems. Counters snowballing
        // without needing per-player problem sets (duels share one fixed problem list).
        List<RoomParticipant> ranked = participantRepo.findByRoomIdOrderByScoreDescLastSolveAtAsc(roomId);
        int leaderScore = ranked.isEmpty() ? 0 : ranked.get(0).getScore();
        int gap = Math.max(0, leaderScore - participant.getScore());
        points += Math.min(gap / 2, 50);

        participant.setScore(participant.getScore() + points);
        participant.setSolvedCount(participant.getSolvedCount() + 1);
        participant.setLastSolveAt(java.time.LocalDateTime.now());
        participantRepo.save(participant);
        return firstBlood;
    }

    /** Duel mode: the round winner gets +1 score (rounds won), then the next round starts after a 5s pause. */
    private void handleDuelRoundWin(Submission submission, Room room) {
        UUID userId = submission.getUser().getId();
        RoomParticipant participant = participantRepo.findByRoomIdAndUserId(room.getId(), userId).orElseGet(() -> {
            RoomParticipant p = new RoomParticipant();
            p.setRoom(room); p.setUser(submission.getUser());
            return p;
        });
        participant.setScore(participant.getScore() + 1);
        participant.setSolvedCount(participant.getSolvedCount() + 1);
        participant.setLastSolveAt(java.time.LocalDateTime.now());
        participantRepo.save(participant);

        ws.convertAndSend("/topic/room/" + room.getId() + "/lobby", Map.of(
            "event", "round_won", "winner", submission.getUser().getUsername(), "round", room.getRoundsPlayed()));

        UUID roomId = room.getId();
        new Thread(() -> {
            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            duelRoundService.advanceRound(roomId);
        }).start();
    }

    private void applyWrongAnswerPenalty(Submission submission) {
        if (submission.getRoom().getProblemCount() == 1) return; // no WA penalty in round-based duel mode
        UUID roomId = submission.getRoom().getId(), userId = submission.getUser().getId();
        boolean isSql = submission.getProblem() == null;
        boolean alreadySolved = (isSql
                ? submissionRepo.findByRoomIdAndSqlSlugAndUserId(roomId, submission.getSqlSlug(), userId)
                : submissionRepo.findByRoomIdAndProblemIdAndUserId(roomId, submission.getProblem().getId(), userId)).stream()
            .anyMatch(s -> "accepted".equals(s.getVerdict()));
        if (alreadySolved) return;

        participantRepo.findByRoomIdAndUserId(roomId, userId).ifPresent(p -> {
            p.setScore(Math.max(0, p.getScore() - 5));
            participantRepo.save(p);
        });
    }

    /** Temporary diagnostic: shows whether the key is set and the raw Judge0 response, to debug why judging fails. */
    public Map<String, Object> diagnoseJudge0() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("apiKeyPresent", !apiKey.isBlank());
        result.put("apiKeyLength", apiKey.length());
        try {
            String body = json.writeValueAsString(Map.of(
                "language_id", 71, "source_code", "print(1)", "stdin", ""
            ));
            var reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(JUDGE0_URL))
                .header("Content-Type", "application/json")
                .header("X-RapidAPI-Host", "judge0-ce.p.rapidapi.com")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(15));
            if (!apiKey.isBlank()) reqBuilder.header("X-RapidAPI-Key", apiKey);
            var resp = HTTP.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            result.put("httpStatus", resp.statusCode());
            result.put("rawBody", resp.body());
        } catch (Exception e) { result.put("error", e.toString()); }
        return result;
    }

    private RunResult run(String code, String language, String input, int timeLimitMs, int memoryMb) {
        try {
            Integer langId = LANG_ID.get(language);
            if (langId == null) return new RunResult("re", "", 0, "Unsupported language: " + language);

            String body = json.writeValueAsString(Map.of(
                "language_id", langId,
                "source_code", code,
                "stdin", input,
                "cpu_time_limit", timeLimitMs / 1000.0,
                "memory_limit", memoryMb * 1024
            ));

            var reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(JUDGE0_URL))
                .header("Content-Type", "application/json")
                .header("X-RapidAPI-Host", "judge0-ce.p.rapidapi.com")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofMillis(timeLimitMs + 20000L));
            if (!apiKey.isBlank()) reqBuilder.header("X-RapidAPI-Key", apiKey);

            var resp = HTTP.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            var root = json.readTree(resp.body());

            int statusId = root.path("status").path("id").asInt();
            String stdout = root.path("stdout").asText("");
            String stderr = root.path("stderr").asText("");
            String compileOut = root.path("compile_output").asText("");
            int runtimeMs = (int)(root.path("time").asDouble(0) * 1000);

            return switch (statusId) {
                case 3 -> new RunResult("ok", stdout, runtimeMs, "");
                case 5 -> new RunResult("tle", "", timeLimitMs, "");
                case 6 -> new RunResult("ce", "", 0, compileOut);
                case 4, 7, 8, 9, 10, 11, 12 -> new RunResult("re", "", runtimeMs, stderr);
                default -> new RunResult("re", "", 0, "Judge error: status " + statusId);
            };
        } catch (Exception e) { return new RunResult("re", "", 0, e.getMessage()); }
    }

    record RunResult(String verdict, String stdout, int runtimeMs, String message) {}
}
