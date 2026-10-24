package com.ccmmd.chess.engineservice.engine;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Picks the engine and turns any failure into a {@link EngineMoveResult} value.
 *
 * <p>Configured with {@code ccmmd.engine.binary}. When it is blank the service falls back to a
 * random legal move and logs it loudly — better a working, obviously-weak opponent than a service
 * that cannot start. See docs/PROGRESS.md for how to point this at Stockfish.
 */
@Component
public class EngineMoveCalculator {

    private static final Logger log = LoggerFactory.getLogger(EngineMoveCalculator.class);

    private final MoveCalculator delegate;

    public EngineMoveCalculator(
            @Value("${ccmmd.engine.binary:}") String binary,
            @Value("${ccmmd.engine.startup-timeout-ms:15000}") long startupTimeoutMs,
            @Value("${ccmmd.engine.move-timeout-ms:15000}") long moveTimeoutMs) {

        if (binary == null || binary.isBlank()) {
            log.warn("ccmmd.engine.binary is not set — using the random-legal fallback. "
                    + "AI games will be very weak. Point it at a UCI engine (e.g. Stockfish) for real play.");
            this.delegate = new RandomLegalEngine();
        } else {
            List<String> command = Arrays.stream(binary.trim().split("\\s+")).toList();
            this.delegate = new UciEngine(command, Duration.ofMillis(startupTimeoutMs), Duration.ofMillis(moveTimeoutMs));
        }
    }

    public String engineName() {
        return delegate.name();
    }

    public EngineMoveResult compute(String fen, String difficulty) {
        try {
            return EngineMoveResult.move(delegate.bestMove(fen, difficulty));
        } catch (EngineException failure) {
            log.warn("engine could not produce a move", failure);
            return EngineMoveResult.failed(failure.getMessage());
        } catch (RuntimeException unexpected) {
            log.warn("unexpected engine failure", unexpected);
            return EngineMoveResult.failed(unexpected.getClass().getSimpleName() + ": " + unexpected.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        if (delegate instanceof UciEngine uciEngine) {
            uciEngine.close();
        }
    }
}
