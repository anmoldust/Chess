package com.ccmmd.chess.analysisservice.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests — no Spring, no database. The heuristic is the thing worth pinning down; the
 * surrounding pipeline is covered by {@code AnalysisServiceTest}.
 */
class AnalysisCalculatorTest {

    private static final String BALANCED =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String WHITE_MINUS_QUEEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNB1KBNR w KQkq - 0 1";
    private static final String BLACK_MINUS_ROOK =
            "rnbqkbn1/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    @Test
    @DisplayName("an empty game is a clean slate, not a crash")
    void emptyGame() {
        AnalysisCalculator.Report report = AnalysisCalculator.review(List.of());

        assertThat(report.movesAnalysed()).isZero();
        assertThat(report.accuracyWhite()).isEqualTo(100.0);
        assertThat(report.accuracyBlack()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("a game with no material change is fully accurate")
    void noMaterialChange() {
        AnalysisCalculator.Report report =
                AnalysisCalculator.review(List.of(BALANCED, BALANCED, BALANCED));

        assertThat(report.accuracyWhite()).isEqualTo(100.0);
        assertThat(report.accuracyBlack()).isEqualTo(100.0);
        assertThat(report.blundersWhite()).isZero();
        assertThat(report.blundersBlack()).isZero();
    }

    @Test
    @DisplayName("losing a queen is a white blunder, wherever the capture happened")
    void losingAQueenIsAWhiteBlunder() {
        AnalysisCalculator.Report report =
                AnalysisCalculator.review(List.of(BALANCED, WHITE_MINUS_QUEEN));

        assertThat(report.blundersWhite()).isEqualTo(1);
        assertThat(report.blundersBlack()).isZero();
        assertThat(report.accuracyWhite()).isZero();
        assertThat(report.accuracyBlack()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("losing a rook is a black blunder")
    void losingARookIsABlackBlunder() {
        AnalysisCalculator.Report report =
                AnalysisCalculator.review(List.of(BALANCED, BLACK_MINUS_ROOK));

        assertThat(report.blundersBlack()).isEqualTo(1);
        assertThat(report.blundersWhite()).isZero();
        assertThat(report.accuracyBlack()).isZero();
    }

    @Test
    @DisplayName("trading a pawn for a pawn is not a blunder")
    void smallMaterialSwingsAreNotBlunders() {
        // White is a pawn down — a real disadvantage, but well below the blunder threshold.
        String whiteMinusPawn =
                "rnbqkbnr/pppppppp/8/8/8/8/PPPP1PPP/RNBQKBNR w KQkq - 0 1";

        AnalysisCalculator.Report report =
                AnalysisCalculator.review(List.of(BALANCED, whiteMinusPawn));

        assertThat(report.blundersWhite()).isZero();
        assertThat(report.accuracyWhite()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("counts one move per position analysed")
    void countsEachPly() {
        AnalysisCalculator.Report report = AnalysisCalculator.review(
                List.of(BALANCED, BALANCED, BALANCED, BALANCED));

        assertThat(report.movesAnalysed()).isEqualTo(4);
    }
}
