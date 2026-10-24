package com.ccmmd.chess.engineservice.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UciProtocolTest {

    @Test
    @DisplayName("reads the move out of a bestmove line")
    void parsesBestMove() {
        assertThat(UciProtocol.parseBestMove("bestmove e2e4")).isEqualTo("e2e4");
        assertThat(UciProtocol.parseBestMove("bestmove e7e8q ponder e2e4")).isEqualTo("e7e8q");
        assertThat(UciProtocol.parseBestMove("  bestmove   b1c3  ")).isEqualTo("b1c3");
    }

    @Test
    @DisplayName("(none) means no legal move, not a failure")
    void parsesNoMove() {
        assertThat(UciProtocol.parseBestMove("bestmove (none)")).isNull();
    }

    @Test
    @DisplayName("ignores lines that are not bestmove")
    void ignoresOtherLines() {
        assertThat(UciProtocol.parseBestMove("info depth 12 score cp 33")).isNull();
        assertThat(UciProtocol.parseBestMove(null)).isNull();
    }
}
