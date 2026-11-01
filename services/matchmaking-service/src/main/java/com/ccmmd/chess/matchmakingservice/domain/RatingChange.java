package com.ccmmd.chess.matchmakingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One player's rating movement from one game.
 *
 * <p>The rating itself only ever holds the current value, so without this a profile could say what
 * a player's rating is but not how it got there. Written in the same transaction as the rating
 * update, so a rating and its history cannot disagree.
 */
@Entity
@Table(name = "rating_change")
public class RatingChange {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "game_id", nullable = false, length = 36)
    private String gameId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "old_rating", nullable = false)
    private int oldRating;

    @Column(name = "new_rating", nullable = false)
    private int newRating;

    @Column(name = "played_at", nullable = false)
    private Instant playedAt;

    protected RatingChange() {
    }

    public RatingChange(String id, String gameId, String userId, int oldRating, int newRating) {
        this.id = id;
        this.gameId = gameId;
        this.userId = userId;
        this.oldRating = oldRating;
        this.newRating = newRating;
        this.playedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getGameId() {
        return gameId;
    }

    public String getUserId() {
        return userId;
    }

    public int getOldRating() {
        return oldRating;
    }

    public int getNewRating() {
        return newRating;
    }

    public Instant getPlayedAt() {
        return playedAt;
    }
}
