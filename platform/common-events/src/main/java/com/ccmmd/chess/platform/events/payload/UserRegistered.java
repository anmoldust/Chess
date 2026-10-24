package com.ccmmd.chess.platform.events.payload;

import java.time.Instant;

/** Emitted by auth-service once an account exists. */
public record UserRegistered(
        String userId,
        String email,
        String displayName,
        Instant registeredAt
) {
}
