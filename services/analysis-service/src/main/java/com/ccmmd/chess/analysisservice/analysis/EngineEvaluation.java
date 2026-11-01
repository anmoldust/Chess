package com.ccmmd.chess.analysisservice.analysis;

/**
 * A source of engine evaluations, in centipawns from the <strong>side to move's</strong>
 * perspective — the convention UCI reports and the one the move-loss maths assumes.
 */
public interface EngineEvaluation {

    String name();

    /**
     * A static evaluation of {@code fen}, searched to {@code depth}.
     *
     * @throws EngineEvaluationException when the engine cannot evaluate the position
     */
    int evaluateCentipawns(String fen, int depth);
}
