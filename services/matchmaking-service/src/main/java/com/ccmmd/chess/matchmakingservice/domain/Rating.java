package com.ccmmd.chess.matchmakingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * A player's competitive rating — the number matchmaking pairs on.
 *
 * <p>It lives here, beside the queue, because a rating exists to decide who plays whom: keeping it
 * in the same service as the matcher means pairing never has to call out for it. It is updated from
 * the {@code game.ended} event, so game-service stays the only thing that decides a result.
 */
@Entity
@Table(name = "ratings")
public class Rating {

    @Id
    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private String userId;

    /** Best-effort label, learned when the player queues; used only for the leaderboard. */
    @Column(name = "display_name", length = 60)
    private String displayName;

    @Column(nullable = false)
    private int rating;

    @Column(name = "games_played", nullable = false)
    private int gamesPlayed;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private int losses;

    @Column(nullable = false)
    private int draws;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Rating() {
    }

    public Rating(String userId, int rating) {
        this.userId = userId;
        this.rating = rating;
        this.updatedAt = Instant.now();
    }

    /** Records one finished game: the resulting rating, and whether this player scored 1, 0.5 or 0. */
    public void record(int newRating, double score) {
        this.rating = newRating;
        this.gamesPlayed += 1;
        if (score > 0.5) {
            this.wins += 1;
        } else if (score < 0.5) {
            this.losses += 1;
        } else {
            this.draws += 1;
        }
        this.updatedAt = Instant.now();
    }

    public void rememberName(String name) {
        if (name != null && !name.isBlank()) {
            String trimmed = name.trim();
            this.displayName = trimmed.length() > 60 ? trimmed.substring(0, 60) : trimmed;
        }
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getRating() {
        return rating;
    }

    public int getGamesPlayed() {
        return gamesPlayed;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public int getDraws() {
        return draws;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
