package com.ccmmd.chess.analysisservice.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the centipawn-loss maths, using a scripted evaluator so the arithmetic is
 * pinned without a process or a database.
 */
class EngineReviewTest {

    /** Returns the scripted evaluation for each position, in order. */
    private static EngineEvaluation scripted(int... evaluations) {
        return new EngineEvaluation() {
            private int next = 0;

            @Override
            public String name() {
                return "scripted";
            }

            @Override
            public int evaluateCentipawns(String fen, int depth) {
                return evaluations[next++];
            }
        };
    }

    @Test
    @DisplayName("a game with no moves is a clean slate")
    void noMoves() {
        AnalysisCalculator.Report report = EngineReview.review(List.of("start"), scripted(0), 12);

        assertThat(report.movesAnalysed()).isZero();
        assertThat(report.accuracyWhite()).isEqualTo(100.0);
        assertThat(report.accuracyBlack()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("giving away a queen is a blunder by the side that moved")
    void hangingAQueenIsABlunder() {
        // White moves from a balanced position into one where black is a queen up.
        AnalysisCalculator.Report report =
                EngineReview.review(List.of("a", "b"), scripted(0, 900), 12);

        assertThat(report.blundersWhite()).isEqualTo(1);
        assertThat(report.blundersBlack()).isZero();
        assertThat(report.accuracyWhite()).isZero();
        assertThat(report.accuracyBlack()).isEqualTo(100.0);
        assertThat(report.movesAnalysed()).isEqualTo(1);
    }

    @Test
    @DisplayName("a blunder is attributed to the mover, whichever color that is")
    void aBlackBlunderIsCountedForBlack() {
        // Move 0 (white) is fine; move 1 (black) hangs a rook.
        AnalysisCalculator.Report report =
                EngineReview.review(List.of("a", "b", "c"), scripted(0, 30, 500), 12);

        assertThat(report.blundersBlack()).isEqualTo(1);
        assertThat(report.blundersWhite()).isZero();
        assertThat(report.accuracyBlack()).isZero();
        assertThat(report.accuracyWhite()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("a small loss is a normal move, not a blunder")
    void smallLossIsNotABlunder() {
        AnalysisCalculator.Report report =
                EngineReview.review(List.of("a", "b"), scripted(0, 80), 12);

        assertThat(report.blundersWhite()).isZero();
        assertThat(report.accuracyWhite()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("an engine-noise improvement is not a blunder")
    void improvementIsNotABlunder() {
        AnalysisCalculator.Report report =
                EngineReview.review(List.of("a", "b"), scripted(0, -250), 12);

        assertThat(report.blundersWhite()).isZero();
    }

    @Test
    @DisplayName("a mate score is clamped so it cannot swamp the loss arithmetic")
    void mateScoresAreClamped() {
        // evaluation[move] + evaluation[move + 1] = 10000 + 10000, clamped to 1000 each => 2000.
        AnalysisCalculator.Report report =
                EngineReview.review(List.of("a", "b"), scripted(10000, 10000), 12);

        assertThat(report.blundersWhite()).isEqualTo(1);
    }
}
