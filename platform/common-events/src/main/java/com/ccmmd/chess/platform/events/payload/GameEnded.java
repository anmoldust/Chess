package com.ccmmd.chess.platform.events.payload;

/** Emitted when a game reaches a terminal state. */
public record GameEnded(
        String gameId,
        String status,
        String result,
        int plyCount,
        String whiteUserId,
        String blackUserId
) {
}
