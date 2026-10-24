package com.ccmmd.chess.platform.events.payload;

import java.time.Instant;

/**
 * A pair has been formed. {@code gameId} is pre-allocated by matchmaking so both clients can
 * navigate to the same game before game-service has finished creating it.
 *
 * <p>Display names travel with the event because game-service does not own accounts and must not
 * call auth-service just to label a seat.
 */
public record MatchFound(
        String matchId,
        String gameId,
        String whiteUserId,
        String blackUserId,
        String whiteDisplayName,
        String blackDisplayName,
        String timeControl,
        Instant matchedAt
) {
}
