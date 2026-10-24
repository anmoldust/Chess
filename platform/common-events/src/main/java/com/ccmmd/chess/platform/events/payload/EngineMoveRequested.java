package com.ccmmd.chess.platform.events.payload;

/** game-service asks engine-service for a move. */
public record EngineMoveRequested(
        String gameId,
        String fen,
        String difficulty,
        String correlationId
) {
}
