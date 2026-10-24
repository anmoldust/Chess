package com.ccmmd.chess.platform.events.payload;

import java.time.Instant;

/** Emitted by game-service when a game is created. {@code aiColor} is null for human games. */
public record GameCreated(
        String gameId,
        String mode,
        String whiteUserId,
        String blackUserId,
        String aiColor,
        Instant createdAt
) {
}
