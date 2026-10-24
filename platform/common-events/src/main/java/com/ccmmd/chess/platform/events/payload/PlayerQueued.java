package com.ccmmd.chess.platform.events.payload;

import java.time.Instant;

/** matchmaking-service confirming a player is queued (the UI waits on this, not on an HTTP call). */
public record PlayerQueued(
        String userId,
        String displayName,
        String timeControl,
        Instant queuedAt
) {
}
