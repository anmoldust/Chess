package com.ccmmd.chess.analysisservice.analysis;

/** Raised when the analysis engine cannot be started or cannot evaluate a position. */
public class EngineEvaluationException extends RuntimeException {

    public EngineEvaluationException(String message) {
        super(message);
    }

    public EngineEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
