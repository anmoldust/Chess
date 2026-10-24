package com.ccmmd.chess.platform.events.payload;

/**
 * engine-service's reply, correlated by {@code correlationId}. A failed result is carried as a
 * value rather than thrown away, so the requester can distinguish "no move exists" from
 * "the engine is broken".
 */
public record EngineMoveComputed(
        String correlationId,
        String gameId,
        String uci,
        String ponder,
        boolean failed,
        String failureReason
) {
}
