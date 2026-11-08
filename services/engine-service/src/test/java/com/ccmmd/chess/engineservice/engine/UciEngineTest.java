package com.ccmmd.chess.engineservice.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives a real child process over the UCI protocol.
 *
 * <p>The stub speaks the protocol but searches nothing, which is exactly right here: this test is
 * about process management and parsing — start-up handshake, reading the answer off a pipe, and
 * what happens when the engine says there is nothing to play.
 */
class UciEngineTest {

    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static List<String> stubCommand() throws URISyntaxException {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (windows) {
            Path script = Paths.get(UciEngineTest.class.getResource("/stub-uci-engine.ps1").toURI());
            return List.of("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString());
        }
        Path script = Paths.get(UciEngineTest.class.getResource("/stub-uci-engine.sh").toURI());
        return List.of("sh", script.toString());
    }

    private static UciEngine startStub() throws URISyntaxException {
        return new UciEngine(stubCommand(), Duration.ofSeconds(60), Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("completes the UCI handshake and returns the engine's move")
    void speaksUciAndReturnsTheMove() throws Exception {
        UciEngine engine = startStub();
        try {
            assertThat(engine.bestMove(START_FEN, "MEDIUM")).isEqualTo("e7e5");
            assertThat(engine.name()).isEqualTo("StubEngine");
        } finally {
            engine.close();
        }
    }

    @Test
    @DisplayName("an engine with no legal move is surfaced as a failure, not a hang")
    void reportsNoLegalMove() throws Exception {
        UciEngine engine = startStub();
        try {
            assertThatThrownBy(() -> engine.bestMove("nomove-position", "MEDIUM"))
                    .isInstanceOf(EngineException.class)
                    .hasMessageContaining("no legal move");
        } finally {
            engine.close();
        }
    }

    @Test
    @DisplayName("an engine binary that does not exist fails fast and says so")
    void missingBinaryFails() {
        UciEngine engine = new UciEngine(
                List.of("definitely-not-a-real-engine-binary"),
                Duration.ofSeconds(5),
                Duration.ofSeconds(5));

        assertThatThrownBy(() -> engine.bestMove(START_FEN, "MEDIUM"))
                .isInstanceOf(EngineException.class);
    }
}
