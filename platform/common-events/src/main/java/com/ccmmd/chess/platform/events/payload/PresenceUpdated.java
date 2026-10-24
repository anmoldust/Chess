package com.ccmmd.chess.platform.events.payload;

import java.time.Instant;

/** A player's online/in-game state changed. */
public record PresenceUpdated(
        String userId,
        boolean online,
        String gameId,
        Instant at
) {
}
