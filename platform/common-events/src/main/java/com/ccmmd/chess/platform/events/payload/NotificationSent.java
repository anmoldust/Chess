package com.ccmmd.chess.platform.events.payload;

import java.time.Instant;

/** A notification was recorded for a user. {@code kind} is e.g. WELCOME, MATCH_FOUND, GAME_ENDED. */
public record NotificationSent(
        String notificationId,
        String userId,
        String kind,
        String message,
        Instant at
) {
}
