package com.ccmmd.chess.gameservice.domain;

import com.github.bhlangonijr.chesslib.Board;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChessRulesTest {

    private static final String START = ChessRules.START_FEN;

    @Test
    @DisplayName("FEN round-trips losslessly from the start position")
    void fenRoundTrips() {
        Board board = ChessRules.board(START);
        assertThat(board.getFen()).isEqualTo(START);
    }

    @Test
    @DisplayName("white moves first from the start position")
    void whiteMovesFirst() {
        assertThat(ChessRules.turnOf(START)).isEqualTo(PlayerColor.WHITE);
    }

    @Nested
    class BasicMoves {

        @Test
        void legalPawnPushApplies() {
            ChessRules.AppliedMove applied = ChessRules.apply(START, "e2", "e4", null);
            assertThat(applied).isNotNull();
            assertThat(applied.uci()).isEqualTo("e2e4");
            assertThat(applied.status()).isEqualTo(GameStatus.IN_PROGRESS);
            assertThat(applied.fenAfter()).startsWith("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b");
        }

        @Test
        void illegalThreeSquarePushIsRejected() {
            assertThat(ChessRules.apply(START, "e2", "e5", null)).isNull();
        }

        @Test
        void movingOpponentsPieceIsRejected() {
            assertThat(ChessRules.apply(START, "e7", "e5", null)).isNull();
        }

        @Test
        void captureOfEmptySquareIsRejected() {
            assertThat(ChessRules.apply(START, "e2", "d4", null)).isNull();
        }

        @Test
        void invalidSquareNotationIsRejected() {
            assertThatThrownBy(() -> ChessRules.apply(START, "z9", "e4", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class SpecialMoves {

        @Test
        @DisplayName("kingside castling is legal and recorded as e1g1")
        void kingsideCastling() {
            String fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";
            ChessRules.AppliedMove applied = ChessRules.apply(fen, "e1", "g1", null);
            assertThat(applied).isNotNull();
            assertThat(applied.uci()).isEqualTo("e1g1");
            assertThat(applied.fenAfter()).contains("RK");
        }

        @Test
        @DisplayName("queenside castling is legal and recorded as e1c1")
        void queensideCastling() {
            String fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";
            ChessRules.AppliedMove applied = ChessRules.apply(fen, "e1", "c1", null);
            assertThat(applied).isNotNull();
            assertThat(applied.uci()).isEqualTo("e1c1");
        }

        @Test
        @DisplayName("castling through an attacked square is rejected")
        void castlingThroughCheckIsRejected() {
            String fen = "4kr2/8/8/8/8/8/8/R3K2R w KQ - 0 1";
            assertThat(ChessRules.apply(fen, "e1", "g1", null)).isNull();
        }

        @Test
        @DisplayName("en passant captures the passed pawn")
        void enPassant() {
            String fen = "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3";
            ChessRules.AppliedMove applied = ChessRules.apply(fen, "e5", "d6", null);
            assertThat(applied).isNotNull();
            assertThat(applied.uci()).isEqualTo("e5d6");
        }

        @Test
        @DisplayName("promotion to queen is legal and recorded with the piece suffix")
        void promotionToQueen() {
            String fen = "4k3/P7/8/8/8/8/8/4K3 w - - 0 1";
            ChessRules.AppliedMove applied = ChessRules.apply(fen, "a7", "a8", "q");
            assertThat(applied).isNotNull();
            assertThat(applied.uci()).isEqualTo("a7a8q");
            assertThat(applied.fenAfter()).contains("Q");
        }

        @Test
        @DisplayName("promotion to knight is legal")
        void promotionToKnight() {
            String fen = "4k3/P7/8/8/8/8/8/4K3 w - - 0 1";
            ChessRules.AppliedMove applied = ChessRules.apply(fen, "a7", "a8", "n");
            assertThat(applied).isNotNull();
            assertThat(applied.uci()).isEqualTo("a7a8n");
        }

        @Test
        @DisplayName("a promotion move without a piece is rejected")
        void promotionWithoutPieceIsRejected() {
            String fen = "4k3/P7/8/8/8/8/8/4K3 w - - 0 1";
            assertThat(ChessRules.apply(fen, "a7", "a8", null)).isNull();
        }
    }

    @Nested
    class TerminalStates {

        @Test
        @DisplayName("checkmate after Fool's Mate is detected as BLACK_WON")
        void checkmate() {
            Board board = ChessRules.board("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3");
            assertThat(ChessRules.evaluateStatus(board)).isEqualTo(GameStatus.BLACK_WON);
        }

        @Test
        @DisplayName("a king with no legal moves that is not in check is stalemate")
        void stalemate() {
            Board board = ChessRules.board("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");
            assertThat(ChessRules.evaluateStatus(board)).isEqualTo(GameStatus.DRAW);
        }

        @Test
        @DisplayName("king versus king is insufficient material")
        void insufficientMaterial() {
            Board board = ChessRules.board("8/8/8/4k3/8/8/8/4K3 w - - 0 1");
            assertThat(ChessRules.evaluateStatus(board)).isEqualTo(GameStatus.DRAW);
        }

        @Test
        @DisplayName("a move leaving your own king in check is rejected")
        void pinnedPieceCannotMove() {
            String fen = "4r3/8/8/8/8/8/4R3/4K3 w - - 0 1";
            assertThat(ChessRules.apply(fen, "e2", "a2", null)).isNull();
        }
    }
}
