package com.offbyone.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offbyone.model.Problem;
import com.offbyone.model.Room;
import com.offbyone.model.RoomParticipant;
import com.offbyone.model.Submission;
import com.offbyone.model.TestCase;
import com.offbyone.repository.RoomParticipantRepository;
import com.offbyone.repository.RoomProblemRepository;
import com.offbyone.repository.SubmissionRepository;
import com.offbyone.repository.TestCaseRepository;
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

    @Value("${judge0.api.key:}")
    private String apiKey;

    public JudgeService(SubmissionRepository submissionRepo, TestCaseRepository testCaseRepo,
                         RoomParticipantRepository participantRepo, RoomProblemRepository roomProblemRepo,
                         SimpMessagingTemplate ws, ObjectMapper json) {
        this.submissionRepo = submissionRepo; this.testCaseRepo = testCaseRepo;
        this.participantRepo = participantRepo; this.roomProblemRepo = roomProblemRepo;
        this.ws = ws; this.json = json;
    }

    @Async
    public void judge(Submission submission) {
        Problem problem = submission.getProblem();
        List<TestCase> testCases = testCaseRepo.findByProblemId(problem.getId());
        String verdict = "accepted";
        int totalRuntime = 0;
        String message = "";

        for (TestCase tc : testCases) {
            RunResult result = run(submission.getCode(), submission.getLanguage(), tc.getInput(), problem.getTimeLimitMs(), problem.getMemoryLimitMb());
            totalRuntime = Math.max(totalRuntime, result.runtimeMs());
            if (result.verdict().equals("ce")) { verdict = "ce"; message = result.message(); break; }
            if (result.verdict().equals("tle")) { verdict = "tle"; break; }
            if (result.verdict().equals("mle")) { verdict = "mle"; break; }
            if (result.verdict().equals("re")) { verdict = "re"; break; }
            if (!result.stdout().trim().equals(tc.getExpectedOutput().trim())) { verdict = "wrong_answer"; break; }
        }

        submission.setVerdict(verdict); submission.setRuntimeMs(totalRuntime);
        submissionRepo.save(submission);
        ws.convertAndSend("/topic/submission/" + submission.getId(),
            Map.of("verdict", verdict, "runtimeMs", totalRuntime, "message", message));

        if (submission.getRoom() != null) {
            if ("accepted".equals(verdict)) awardPoints(submission);
            else if ("wrong_answer".equals(verdict)) applyWrongAnswerPenalty(submission);
            ws.convertAndSend("/topic/room/" + submission.getRoom().getId() + "/submission",
                Map.of("submissionId", submission.getId(), "userId", submission.getUser().getId(),
                       "problemId", submission.getProblem().getId(), "verdict", verdict));
        }
    }

    private void awardPoints(Submission submission) {
        UUID roomId = submission.getRoom().getId(), problemId = submission.getProblem().getId(), userId = submission.getUser().getId();
        boolean alreadySolved = submissionRepo.findByRoomIdAndProblemIdAndUserId(roomId, problemId, userId).stream()
            .anyMatch(s -> "accepted".equals(s.getVerdict()) && !s.getId().equals(submission.getId()));
        if (alreadySolved) return;

        boolean firstBlood = submissionRepo.findByRoomIdAndProblemIdAndVerdict(roomId, problemId, "accepted").stream()
            .noneMatch(s -> !s.getId().equals(submission.getId()));

        int basePoints = roomProblemRepo.findByRoomIdAndProblemId(roomId, problemId).map(rp -> rp.getPoints()).orElse(100);
        Room room = submission.getRoom();
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
    }

    private void applyWrongAnswerPenalty(Submission submission) {
        UUID roomId = submission.getRoom().getId(), problemId = submission.getProblem().getId(), userId = submission.getUser().getId();
        boolean alreadySolved = submissionRepo.findByRoomIdAndProblemIdAndUserId(roomId, problemId, userId).stream()
            .anyMatch(s -> "accepted".equals(s.getVerdict()));
        if (alreadySolved) return;

        participantRepo.findByRoomIdAndUserId(roomId, userId).ifPresent(p -> {
            p.setScore(Math.max(0, p.getScore() - 5));
            participantRepo.save(p);
        });
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
