package com.ccmmd.chess.analysisservice.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Drives a UCI engine child process to evaluate positions.
 *
 * <p>A trimmed sibling of engine-service's {@code UciEngine}: this one only needs a score, not a
 * best move, so it skips the difficulty/skill plumbing and reads {@code info ... score} lines.
 *
 * <p>Output is drained by a daemon thread into a queue rather than read inline: a blocking
 * {@code readLine()} cannot be given a deadline, and an engine that hangs would otherwise hang the
 * consumer thread that owns it. The process starts lazily and is restarted if it dies, so a crashed
 * engine is one failed analysis, not a dead service.
 */
public class UciEvaluationEngine implements EngineEvaluation {

    private static final Logger log = LoggerFactory.getLogger(UciEvaluationEngine.class);

    private final List<String> command;
    private final Duration startupTimeout;
    private final Duration evalTimeout;

    private Process process;
    private BufferedWriter stdin;
    private final BlockingQueue<String> output = new LinkedBlockingQueue<>();
    private volatile String engineName = "uci-engine";

    public UciEvaluationEngine(List<String> command, Duration startupTimeout, Duration evalTimeout) {
        this.command = List.copyOf(command);
        this.startupTimeout = startupTimeout;
        this.evalTimeout = evalTimeout;
    }

    @Override
    public String name() {
        return engineName;
    }

    @Override
    public synchronized int evaluateCentipawns(String fen, int depth) {
        startIfNeeded();

        send("isready");
        awaitLine("readyok"::equals, startupTimeout, "engine never became ready");
        send("position fen " + fen);
        send("go depth " + depth);

        return awaitScore();
    }

    public synchronized void close() {
        if (process == null) {
            return;
        }
        try {
            send("quit");
        } catch (EngineEvaluationException ignored) {
            // Already gone; destroying it below is enough.
        }
        process.destroy();
        process = null;
    }

    private void startIfNeeded() {
        if (process != null && process.isAlive()) {
            return;
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            process = builder.start();
            stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            output.clear();

            Thread reader = new Thread(this::drainOutput, "uci-eval-reader");
            reader.setDaemon(true);
            reader.start();

            send("uci");
            awaitLine("uciok"::equals, startupTimeout, "engine did not answer the uci handshake");
            log.info("analysis engine started: {} ({})", engineName, String.join(" ", command));
        } catch (IOException failure) {
            throw new EngineEvaluationException("Cannot start engine: " + String.join(" ", command), failure);
        }
    }

    private void drainOutput() {
        Process current = process;
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(current.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("id name ")) {
                    engineName = line.substring("id name ".length()).trim();
                }
                output.put(line);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException failure) {
            log.debug("engine output stream ended", failure);
        }
    }

    /** Reads {@code info} lines, keeping the last score, until {@code bestmove} ends the search. */
    private int awaitScore() {
        long deadline = System.nanoTime() + evalTimeout.toNanos();
        Integer score = null;
        while (true) {
            String line = poll(deadline, "engine did not evaluate the position in time");
            if (line.trim().startsWith("bestmove")) {
                break;
            }
            Integer found = scoreOf(line);
            if (found != null) {
                score = found;
            }
        }
        if (score == null) {
            // A legal-but-terminal position (mate/stalemate) can report no score. Zero is the
            // honest neutral rather than failing the whole review over one edge position.
            log.debug("engine reported no score for position; treating as balanced");
            return 0;
        }
        return score;
    }

    /**
     * Pulls the score out of an {@code info} line: {@code score cp N} (centipawns) or
     * {@code score mate N} (plies to mate, negative when the side to move is being mated), mapped
     * to a large centipawn value that the reviewer clamps.
     */
    static Integer scoreOf(String line) {
        if (line == null) {
            return null;
        }
        String[] parts = line.trim().split("\\s+");
        for (int i = 0; i + 2 < parts.length; i++) {
            if (!"score".equals(parts[i])) {
                continue;
            }
            try {
                int value = Integer.parseInt(parts[i + 2]);
                return switch (parts[i + 1]) {
                    case "cp" -> value;
                    case "mate" -> value > 0 ? 10000 - value : -10000 - value;
                    default -> null;
                };
            } catch (NumberFormatException malformed) {
                return null;
            }
        }
        return null;
    }

    private void awaitLine(Predicate<String> predicate, Duration timeout, String failureMessage) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            String line = poll(deadline, failureMessage);
            if (predicate.test(line)) {
                return;
            }
        }
    }

    private String poll(long deadlineNanos, String failureMessage) {
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0) {
            throw new EngineEvaluationException(failureMessage);
        }
        try {
            String line = output.poll(remaining, TimeUnit.NANOSECONDS);
            if (line == null) {
                throw new EngineEvaluationException(failureMessage);
            }
            return line;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new EngineEvaluationException(failureMessage + " (interrupted)");
        }
    }

    private void send(String commandLine) {
        try {
            stdin.write(commandLine);
            stdin.write("\n");
            stdin.flush();
        } catch (IOException failure) {
            throw new EngineEvaluationException("Cannot write to the engine: " + commandLine, failure);
        }
    }
}
