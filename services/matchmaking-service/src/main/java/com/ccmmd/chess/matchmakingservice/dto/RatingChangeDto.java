package com.ccmmd.chess.matchmakingservice.dto;

import com.ccmmd.chess.matchmakingservice.domain.RatingChange;

import java.time.Instant;

/** One rating movement, for a profile's progression list. {@code delta} is the signed change. */
public record RatingChangeDto(
        String gameId,
        int oldRating,
        int newRating,
        int delta,
        Instant playedAt
) {

    public static RatingChangeDto of(RatingChange change) {
        return new RatingChangeDto(
                change.getGameId(),
                change.getOldRating(),
                change.getNewRating(),
                change.getNewRating() - change.getOldRating(),
                change.getPlayedAt());
    }
}
