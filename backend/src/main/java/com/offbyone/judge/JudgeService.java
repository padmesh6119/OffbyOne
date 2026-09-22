package com.offbyone.judge;

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

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
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

    private static final int STDOUT_CAP_BYTES = 2 * 1024 * 1024;
    private volatile Boolean bwrapAvailable;

    private boolean bwrapAvailable() {
        if (bwrapAvailable == null) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v bwrap").start();
                bwrapAvailable = p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
            } catch (Exception e) { bwrapAvailable = false; }
        }
        return bwrapAvailable;
    }

    /** Sandboxes a shell command: no network, no host env vars, read-only rootfs except the submission's own tmpDir, resource-limited. */
    private List<String> sandboxed(Path tmpDir, String shellCmd, int memoryMb, String language) {
        String javaHome = System.getProperty("java.home");
        // ulimit -v (RLIMIT_AS) is incompatible with the JVM: it reserves far more virtual
        // address space than it actually uses (compressed class space alone defaults to 1GB),
        // so capping -v kills javac/java at startup regardless of memoryMb. Heap is capped via
        // -Xmx instead for java; -v still applies for python/cpp, which don't have this issue.
        String vlimit = "java".equals(language) ? "" : ("ulimit -v " + (memoryMb * 1024) + " 2>/dev/null; ");
        String guarded = vlimit + "ulimit -u 32 2>/dev/null; ulimit -f 20480 2>/dev/null; " + shellCmd;
        List<String> cmd = new ArrayList<>(List.of(
            "bwrap",
            "--ro-bind", "/usr", "/usr",
            "--ro-bind", "/lib", "/lib",
            "--ro-bind-try", "/lib64", "/lib64",
            "--ro-bind-try", "/opt", "/opt",
            "--ro-bind-try", "/etc", "/etc"));
        if (javaHome != null && !javaHome.startsWith("/usr") && !javaHome.startsWith("/opt")) {
            cmd.addAll(List.of("--ro-bind-try", javaHome, javaHome));
        }
        cmd.addAll(List.of(
            // --tmpfs /tmp must precede the tmpDir bind below — bwrap applies mounts in
            // argument order, and tmpDir lives under /tmp, so a later tmpfs would shadow it.
            "--tmpfs", "/tmp",
            "--bind", tmpDir.toString(), tmpDir.toString(),
            "--proc", "/proc",
            "--dev", "/dev",
            "--unshare-net",
            "--unshare-pid",
            "--die-with-parent",
            "--new-session",
            "--clearenv",
            "--setenv", "PATH", javaHome + "/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "--setenv", "JAVA_HOME", javaHome,
            "--chdir", tmpDir.toString(),
            "sh", "-c", guarded
        ));
        return cmd;
    }

    /** Temporary diagnostic: runs the actual sandbox invocation and reports raw output, to debug why bwrap fails on a given host. */
    public Map<String, Object> diagnoseSandbox() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        try {
            Process which = new ProcessBuilder("sh", "-c", "command -v bwrap; echo EXIT:$?").redirectErrorStream(true).start();
            String whichOut = new String(which.getInputStream().readAllBytes());
            which.waitFor();
            result.put("which_bwrap", whichOut.trim());
        } catch (Exception e) { result.put("which_bwrap_error", e.toString()); }

        try {
            Path tmp = Files.createTempDirectory("sandbox-diag");
            List<String> cmd = sandboxed(tmp, "echo sandboxed-ok", 256, "python");
            result.put("bwrap_cmd", cmd);
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            boolean finished = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            result.put("bwrap_test_output", out.trim());
            result.put("bwrap_test_exit", finished ? String.valueOf(p.exitValue()) : "timeout");
            cleanup(tmp);
        } catch (Exception e) { result.put("bwrap_test_error", e.toString()); }

        return result;
    }

    private RunResult run(String code, String language, String input, int timeLimitMs, int memoryMb) {
        if (!bwrapAvailable()) return new RunResult("re", "", 0, "judge sandbox unavailable");
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
                Process compile = new ProcessBuilder(sandboxed(tmpDir, compileCmd, memoryMb, language)).redirectErrorStream(true).start();
                BoundedOutputStream compileOut = new BoundedOutputStream(STDOUT_CAP_BYTES);
                Thread compileDrain = new Thread(() -> { try { compile.getInputStream().transferTo(compileOut); } catch (IOException ignored) {} });
                compileDrain.start();
                boolean compileFinished = compile.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                if (!compileFinished) { compile.destroyForcibly(); compileDrain.join(); cleanup(tmpDir); return new RunResult("ce", "", 0, "compile timed out"); }
                compileDrain.join();
                if (compile.exitValue() != 0) { cleanup(tmpDir); return new RunResult("ce", "", 0, compileOut.result()); }
            }
            String runCmd = switch (language) {
                case "java" -> "java -Xmx" + memoryMb + "m Main"; case "cpp" -> "./main"; case "python" -> "python3 main.py";
                default -> throw new IllegalArgumentException();
            };
            long start = System.currentTimeMillis();
            Process run = new ProcessBuilder(sandboxed(tmpDir, runCmd + " < input.txt", memoryMb, language)).redirectErrorStream(true).start();
            BoundedOutputStream stdoutBuf = new BoundedOutputStream(STDOUT_CAP_BYTES);
            Thread drain = new Thread(() -> { try { run.getInputStream().transferTo(stdoutBuf); } catch (IOException ignored) {} });
            drain.start();
            boolean finished = run.waitFor(timeLimitMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            int runtimeMs = (int)(System.currentTimeMillis() - start);
            if (!finished) { run.destroyForcibly(); drain.join(); cleanup(tmpDir); return new RunResult("tle", "", runtimeMs, ""); }
            drain.join();
            if (run.exitValue() != 0) { cleanup(tmpDir); return new RunResult("re", "", runtimeMs, ""); }
            String stdout = stdoutBuf.result();
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

    /** Caps how much child stdout/stderr we hold in memory, while still draining the pipe so the child never blocks on a full buffer. */
    private static class BoundedOutputStream extends OutputStream {
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private final int cap;
        BoundedOutputStream(int cap) { this.cap = cap; }
        @Override public void write(int b) { if (buf.size() < cap) buf.write(b); }
        @Override public void write(byte[] b, int off, int len) {
            int remaining = cap - buf.size();
            if (remaining <= 0) return;
            buf.write(b, off, Math.min(len, remaining));
        }
        String result() { return buf.toString(); }
    }

    record RunResult(String verdict, String stdout, int runtimeMs, String message) {}
}
