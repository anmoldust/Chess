package com.ccmmd.chess.engineservice.engine;

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
 * Speaks UCI to a child process.
 *
 * <p>Output is drained by a daemon reader thread into a queue rather than read inline: a blocking
 * {@code readLine()} cannot be given a deadline, and an engine that hangs would otherwise hang the
 * consumer thread that owns it — and with it the whole inbox.
 *
 * <p>The process is started lazily and restarted if it dies, so a crashed engine is a single failed
 * request rather than a permanently dead service.
 */
public class UciEngine implements MoveCalculator {

    private static final Logger log = LoggerFactory.getLogger(UciEngine.class);

    private final List<String> command;
    private final Duration startupTimeout;
    private final Duration moveTimeout;

    private Process process;
    private BufferedWriter stdin;
    private final BlockingQueue<String> output = new LinkedBlockingQueue<>();
    private volatile String engineName = "uci-engine";

    public UciEngine(List<String> command, Duration startupTimeout, Duration moveTimeout) {
        this.command = List.copyOf(command);
        this.startupTimeout = startupTimeout;
        this.moveTimeout = moveTimeout;
    }

    @Override
    public String name() {
        return engineName;
    }

    @Override
    public synchronized String bestMove(String fen, String difficulty) {
        startIfNeeded();

        EngineDifficulty level = EngineDifficulty.from(difficulty);
        send("setoption name Skill Level value " + level.skill());
        send("isready");
        awaitLine("readyok"::equals, startupTimeout, "engine never became ready");

        send("position fen " + fen);
        send("go depth " + level.depth());

        String bestMove = awaitBestMove();
        if (bestMove == null) {
            throw new EngineException("Engine reported no legal move for the given position");
        }
        return bestMove;
    }

    /** Closes the process politely; used by the shutdown hook. */
    public synchronized void close() {
        if (process == null) {
            return;
        }
        try {
            send("quit");
        } catch (EngineException ignored) {
            // The engine is already gone; destroying it below is enough.
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

            Thread reader = new Thread(this::drainOutput, "uci-engine-reader");
            reader.setDaemon(true);
            reader.start();

            send("uci");
            awaitLine("uciok"::equals, startupTimeout, "engine did not answer the uci handshake");
            log.info("engine started: {} ({})", engineName, String.join(" ", command));
        } catch (IOException failure) {
            throw new EngineException("Cannot start engine: " + String.join(" ", command), failure);
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

    private String awaitBestMove() {
        long deadline = System.nanoTime() + moveTimeout.toNanos();
        while (true) {
            String line = poll(deadline, "engine did not return a move in time");
            if (line.trim().startsWith("bestmove")) {
                return UciProtocol.parseBestMove(line);
            }
        }
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
            throw new EngineException(failureMessage);
        }
        try {
            String line = output.poll(remaining, TimeUnit.NANOSECONDS);
            if (line == null) {
                throw new EngineException(failureMessage);
            }
            return line;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new EngineException(failureMessage + " (interrupted)");
        }
    }

    private void send(String commandLine) {
        try {
            stdin.write(commandLine);
            stdin.write("\n");
            stdin.flush();
        } catch (IOException failure) {
            throw new EngineException("Cannot write to the engine: " + commandLine, failure);
        }
    }
}
