package com.ccmmd.chess.analysisservice.analysis;

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
 * Drives a real child process over UCI. The stub reports a fixed score, which is what this test is
 * about: process start-up, reading a score off the pipe, mate-score translation, and failing fast
 * when the binary does not exist.
 */
class UciEvaluationEngineTest {

    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static List<String> stubCommand() throws URISyntaxException {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (windows) {
            Path script = Paths.get(UciEvaluationEngineTest.class.getResource("/stub-uci-evaluator.ps1").toURI());
            return List.of("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString());
        }
        Path script = Paths.get(UciEvaluationEngineTest.class.getResource("/stub-uci-evaluator.sh").toURI());
        return List.of("sh", script.toString());
    }

    @Test
    @DisplayName("evaluates a position and reads the centipawn score")
    void evaluatesCentipawns() throws Exception {
        UciEvaluationEngine engine = new UciEvaluationEngine(
                stubCommand(), Duration.ofSeconds(60), Duration.ofSeconds(60));
        try {
            assertThat(engine.evaluateCentipawns(START_FEN, 12)).isEqualTo(150);
            assertThat(engine.name()).isEqualTo("StubEvaluator");
        } finally {
            engine.close();
        }
    }

    @Test
    @DisplayName("translates a mate score into a large centipawn value")
    void translatesMateScore() throws Exception {
        UciEvaluationEngine engine = new UciEvaluationEngine(
                stubCommand(), Duration.ofSeconds(60), Duration.ofSeconds(60));
        try {
            assertThat(engine.evaluateCentipawns("mate-in-two", 12)).isEqualTo(9998);
        } finally {
            engine.close();
        }
    }

    @Test
    @DisplayName("a binary that does not exist fails fast and says so")
    void missingBinaryFails() {
        UciEvaluationEngine engine = new UciEvaluationEngine(
                List.of("definitely-not-a-real-engine-binary"),
                Duration.ofSeconds(5),
                Duration.ofSeconds(5));

        assertThatThrownBy(() -> engine.evaluateCentipawns(START_FEN, 12))
                .isInstanceOf(EngineEvaluationException.class);
    }

    @Test
    @DisplayName("parses the score out of an info line, including mate")
    void parsesScoreLines() {
        assertThat(UciEvaluationEngine.scoreOf("info depth 12 score cp 34 nodes 100 pv e2e4")).isEqualTo(34);
        assertThat(UciEvaluationEngine.scoreOf("info depth 12 score cp -120 pv e2e4")).isEqualTo(-120);
        assertThat(UciEvaluationEngine.scoreOf("info depth 12 score mate 3")).isEqualTo(9997);
        assertThat(UciEvaluationEngine.scoreOf("info depth 12 score mate -2")).isEqualTo(-9998);
        assertThat(UciEvaluationEngine.scoreOf("info depth 12 nodes 100")).isNull();
    }
}
