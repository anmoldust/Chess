package com.ccmmd.chess.analysisservice.analysis;

import java.util.List;

/**
 * A move review built from engine evaluations.
 *
 * <p>For every move it compares the engine's assessment of the position the mover faced with the
 * assessment after the move, from the mover's point of view. The difference — the centipawn loss —
 * is what the played move gave away relative to best play. A large loss is a blunder.
 *
 * <p>This is the engine-backed counterpart to {@link AnalysisCalculator}: same report shape, so the
 * pipeline and the UI are unchanged, but the numbers now reflect a search rather than a material
 * count, so quiet positional mistakes count too.
 */
public final class EngineReview {

    /** A move that gives away roughly a knight or more is a blunder. */
    private static final int BLUNDER_CP = 300;

    /**
     * Evaluations are clamped before differencing. Engines report mate and tablebase scores as very
     * large numbers; without this a forced mate would swamp the loss arithmetic for every move
     * leading up to it.
     */
    private static final int CLAMP_CP = 1000;

    private EngineReview() {
    }

    /**
     * @param positions every position the game held, <strong>including the start position</strong>,
     *                  so each move has a position before and after it
     */
    public static AnalysisCalculator.Report review(
            List<String> positions, EngineEvaluation engine, int depth) {
        int moves = positions.size() - 1;
        if (moves <= 0) {
            return new AnalysisCalculator.Report(100.0, 100.0, 0, 0, 0);
        }

        int[] evaluation = new int[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            evaluation[i] = clamp(engine.evaluateCentipawns(positions.get(i), depth));
        }

        int blundersWhite = 0;
        int blundersBlack = 0;
        for (int move = 0; move < moves; move++) {
            // evaluation[move] is the mover's view before moving; evaluation[move + 1] is the
            // opponent's view after. Adding them puts both on the mover's scale; a negative value
            // is an engine-noise improvement, not a loss, so it is floored at zero.
            int loss = Math.max(0, evaluation[move] + evaluation[move + 1]);
            if (loss >= BLUNDER_CP) {
                if (move % 2 == 0) {
                    blundersWhite++;
                } else {
                    blundersBlack++;
                }
            }
        }

        int whiteMoves = (moves + 1) / 2;
        int blackMoves = moves / 2;
        return new AnalysisCalculator.Report(
                accuracy(whiteMoves, blundersWhite),
                accuracy(blackMoves, blundersBlack),
                blundersWhite,
                blundersBlack,
                moves);
    }

    private static int clamp(int centipawns) {
        return Math.max(-CLAMP_CP, Math.min(CLAMP_CP, centipawns));
    }

    private static double accuracy(int moves, int blunders) {
        if (moves == 0) {
            return 100.0;
        }
        return Math.round(100.0 * (moves - blunders) / moves * 10.0) / 10.0;
    }
}
