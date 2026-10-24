package com.ccmmd.chess.gameservice;

import com.ccmmd.chess.gameservice.domain.ChessRules;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Perft is the acceptance test for move generation. Node counts are the published values
 * from the Chess Programming Wiki; any deviation means move generation is wrong.
 *
 * <p>These tests moved here unchanged from the pre-split monolith and are the regression net
 * for the whole microservice refactor: if extracting game-service broke the rules, this fails.
 */
class PerftTest {

    private static long perft(Board board, int depth) {
        if (depth == 0) {
            return 1L;
        }
        long nodes = 0L;
        for (Move move : board.legalMoves()) {
            board.doMove(move);
            nodes += perft(board, depth - 1);
            board.undoMove();
        }
        return nodes;
    }

    @Test
    @DisplayName("start position perft matches published counts")
    void startPosition() {
        Board board = ChessRules.board(ChessRules.START_FEN);
        assertThat(perft(board, 1)).isEqualTo(20L);
        assertThat(perft(board, 2)).isEqualTo(400L);
        assertThat(perft(board, 3)).isEqualTo(8_902L);
        assertThat(perft(board, 4)).isEqualTo(197_281L);
    }

    @Test
    @DisplayName("Kiwipete perft exercises castling and en passant")
    void kiwipete() {
        Board board = ChessRules.board(
                "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        assertThat(perft(board, 1)).isEqualTo(48L);
        assertThat(perft(board, 2)).isEqualTo(2_039L);
        assertThat(perft(board, 3)).isEqualTo(97_862L);
    }

    @Test
    @DisplayName("endgame perft exercises promotion and checks")
    void endgamePosition() {
        Board board = ChessRules.board("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1");
        assertThat(perft(board, 1)).isEqualTo(14L);
        assertThat(perft(board, 2)).isEqualTo(191L);
        assertThat(perft(board, 3)).isEqualTo(2_812L);
        assertThat(perft(board, 4)).isEqualTo(43_238L);
    }
}
