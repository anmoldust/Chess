package com.ccmmd.chess.engineservice.engine;

/**
 * The outcome of asking for a move. Failure is carried as a value, not thrown, so the requester
 * can distinguish "this position has no legal move" from "the engine is broken" — and so a
 * transient engine fault does not poison-loop through the dead-letter queue.
 */
public record EngineMoveResult(String uci, boolean failed, String failureReason) {

    public static EngineMoveResult move(String uci) {
        return new EngineMoveResult(uci, false, null);
    }

    public static EngineMoveResult noMoveAvailable() {
        return new EngineMoveResult(null, false, null);
    }

    public static EngineMoveResult failed(String reason) {
        return new EngineMoveResult(null, true, reason);
    }
}
