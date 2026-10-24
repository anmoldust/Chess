package com.ccmmd.chess.engineservice.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RandomLegalEngineTest {

    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private final RandomLegalEngine engine = new RandomLegalEngine();

    @Test
    @DisplayName("returns a plausible move from the start position")
    void returnsALegalLookingMove() {
        for (int attempt = 0; attempt < 20; attempt++) {
            assertThat(engine.bestMove(START_FEN, "MEDIUM")).matches("[a-h][1-8][a-h][1-8][qrbn]?");
        }
    }

    @Test
    @DisplayName("says so in its name, so nobody mistakes it for a real engine")
    void isHonestAboutBeingWeak() {
        assertThat(engine.name()).contains("fallback");
    }

    @Test
    @DisplayName("a position with no legal moves is an engine failure, not a crash")
    void noLegalMovesIsReported() {
        String checkmate = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3";
        assertThatThrownBy(() -> engine.bestMove(checkmate, "MEDIUM"))
                .isInstanceOf(EngineException.class);
    }
}
