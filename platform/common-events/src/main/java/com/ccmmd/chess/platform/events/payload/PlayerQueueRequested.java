package com.ccmmd.chess.platform.events.payload;

/** A player asking to be matched. */
public record PlayerQueueRequested(
        String userId,
        String displayName,
        String timeControl,
        String preferredColor
) {
}
