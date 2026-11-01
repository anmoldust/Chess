package com.ccmmd.chess.matchmakingservice.dto;

import com.ccmmd.chess.matchmakingservice.domain.Rating;

/** A player's standing: the rating plus the record behind it, for display and the leaderboard. */
public record RatingDto(
        String userId,
        String displayName,
        int rating,
        int gamesPlayed,
        int wins,
        int losses,
        int draws
) {

    public static RatingDto of(Rating rating) {
        return new RatingDto(
                rating.getUserId(),
                rating.getDisplayName(),
                rating.getRating(),
                rating.getGamesPlayed(),
                rating.getWins(),
                rating.getLosses(),
                rating.getDraws());
    }
}
