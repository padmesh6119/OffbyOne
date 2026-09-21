package com.offbyone.judge;

import com.offbyone.model.Problem;
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

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JudgeService {
    private final SubmissionRepository submissionRepo;
    private final TestCaseRepository testCaseRepo;
    private final RoomParticipantRepository participantRepo;
    private final RoomProblemRepository roomProblemRepo;
    private final SimpMessagingTemplate ws;

    public JudgeService(SubmissionRepository submissionRepo, TestCaseRepository testCaseRepo,
                         RoomParticipantRepository participantRepo, RoomProblemRepository roomProblemRepo,
                         SimpMessagingTemplate ws) {
        this.submissionRepo = submissionRepo; this.testCaseRepo = testCaseRepo;
        this.participantRepo = participantRepo; this.roomProblemRepo = roomProblemRepo; this.ws = ws;
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

        int points = roomProblemRepo.findByRoomIdAndProblemId(roomId, problemId).map(rp -> rp.getPoints()).orElse(100);
        RoomParticipant participant = participantRepo.findByRoomIdAndUserId(roomId, userId).orElseGet(() -> {
            RoomParticipant p = new RoomParticipant();
            p.setRoom(submission.getRoom()); p.setUser(submission.getUser());
            return p;
        });
        participant.setScore(participant.getScore() + points);
        participantRepo.save(participant);
    }

    private RunResult run(String code, String language, String input, int timeLimitMs, int memoryMb) {
        try {
            Path tmpDir = Files.createTempDirectory("judge-" + UUID.randomUUID());
            String filename = switch (language) {
                case "java" -> "Main.java"; case "python" -> "main.py"; case "cpp" -> "main.cpp";
                default -> throw new IllegalArgumentException("Unsupported: " + language);
            };
            Files.writeString(tmpDir.resolve(filename), code);
            Files.writeString(tmpDir.resolve("input.txt"), input);
            String compileCmd = switch (language) {
                case "java" -> "javac Main.java"; case "cpp" -> "g++ -O2 -o main main.cpp"; default -> null;
            };
            if (compileCmd != null) {
                Process compile = new ProcessBuilder("sh", "-c", compileCmd).directory(tmpDir.toFile()).redirectErrorStream(true).start();
                ByteArrayOutputStream compileOut = new ByteArrayOutputStream();
                Thread compileDrain = new Thread(() -> { try { compile.getInputStream().transferTo(compileOut); } catch (IOException ignored) {} });
                compileDrain.start();
                compile.waitFor();
                compileDrain.join();
                if (compile.exitValue() != 0) { cleanup(tmpDir); return new RunResult("ce", "", 0, compileOut.toString()); }
            }
            String runCmd = switch (language) {
                case "java" -> "java -Xmx" + memoryMb + "m Main"; case "cpp" -> "./main"; case "python" -> "python3 main.py";
                default -> throw new IllegalArgumentException();
            };
            long start = System.currentTimeMillis();
            Process run = new ProcessBuilder("sh", "-c", runCmd + " < input.txt").directory(tmpDir.toFile()).redirectErrorStream(true).start();
            ByteArrayOutputStream stdoutBuf = new ByteArrayOutputStream();
            Thread drain = new Thread(() -> { try { run.getInputStream().transferTo(stdoutBuf); } catch (IOException ignored) {} });
            drain.start();
            boolean finished = run.waitFor(timeLimitMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            int runtimeMs = (int)(System.currentTimeMillis() - start);
            if (!finished) { run.destroyForcibly(); drain.join(); cleanup(tmpDir); return new RunResult("tle", "", runtimeMs, ""); }
            drain.join();
            if (run.exitValue() != 0) { cleanup(tmpDir); return new RunResult("re", "", runtimeMs, ""); }
            String stdout = stdoutBuf.toString();
            cleanup(tmpDir);
            return new RunResult("ok", stdout, runtimeMs, "");
        } catch (Exception e) { return new RunResult("re", "", 0, ""); }
    }

    private void cleanup(Path dir) {
        try {
            try (var stream = Files.walk(dir)) {
                stream.sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        } catch (IOException ignored) {}
    }

    record RunResult(String verdict, String stdout, int runtimeMs, String message) {}
}
