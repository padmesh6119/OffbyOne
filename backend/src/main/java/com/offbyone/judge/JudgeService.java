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
    private static final String PISTON_URL = "https://emkc.org/api/v2/piston/execute";

    private final SubmissionRepository submissionRepo;
    private final TestCaseRepository testCaseRepo;
    private final RoomParticipantRepository participantRepo;
    private final RoomProblemRepository roomProblemRepo;
    private final SimpMessagingTemplate ws;
    private final ObjectMapper json;

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
            String pistonLang = switch (language) {
                case "java" -> "java"; case "python" -> "python"; case "cpp" -> "c++";
                default -> throw new IllegalArgumentException("Unsupported: " + language);
            };
            String filename = switch (language) {
                case "java" -> "Main.java"; case "python" -> "main.py"; case "cpp" -> "main.cpp";
                default -> throw new IllegalArgumentException();
            };
            String body = json.writeValueAsString(Map.of(
                "language", pistonLang, "version", "*",
                "files", List.of(Map.of("name", filename, "content", code)),
                "stdin", input,
                "run_timeout", timeLimitMs,
                "compile_timeout", 10000
            ));
            var req = HttpRequest.newBuilder().uri(URI.create(PISTON_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofMillis(timeLimitMs + 20000L))
                .build();
            var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            var root = json.readTree(resp.body());
            if (root.has("compile") && root.get("compile").get("code").asInt() != 0)
                return new RunResult("ce", "", 0, root.get("compile").get("stderr").asText());
            var run = root.get("run");
            String signal = run.has("signal") && !run.get("signal").isNull() ? run.get("signal").asText() : "";
            if ("SIGKILL".equals(signal) || "SIGTERM".equals(signal)) return new RunResult("tle", "", timeLimitMs, "");
            if (run.get("code").asInt() != 0) return new RunResult("re", "", 0, run.get("stderr").asText());
            return new RunResult("ok", run.get("stdout").asText(), 0, "");
        } catch (Exception e) { return new RunResult("re", "", 0, e.getMessage()); }
    }

    record RunResult(String verdict, String stdout, int runtimeMs, String message) {}
}
