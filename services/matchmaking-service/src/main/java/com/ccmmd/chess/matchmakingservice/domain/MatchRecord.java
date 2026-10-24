package com.ccmmd.chess.matchmakingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A pairing. {@code gameId} is allocated here, before game-service has created anything, so both
 * players can be told where to go in the same event that announces the match.
 */
@Entity
@Table(name = "matches")
public class MatchRecord {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "game_id", nullable = false, unique = true, length = 36)
    private String gameId;

    @Column(name = "white_user_id", nullable = false, length = 36)
    private String whiteUserId;

    @Column(name = "black_user_id", nullable = false, length = 36)
    private String blackUserId;

    @Column(name = "time_control", nullable = false, length = 24)
    private String timeControl;

    @Column(name = "matched_at", nullable = false)
    private Instant matchedAt;

    protected MatchRecord() {
    }

    public MatchRecord(String id, String gameId, String whiteUserId, String blackUserId, String timeControl) {
        this.id = id;
        this.gameId = gameId;
        this.whiteUserId = whiteUserId;
        this.blackUserId = blackUserId;
        this.timeControl = timeControl;
        this.matchedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getGameId() {
        return gameId;
    }

    public String getWhiteUserId() {
        return whiteUserId;
    }

    public String getBlackUserId() {
        return blackUserId;
    }

    public String getTimeControl() {
        return timeControl;
    }

    public Instant getMatchedAt() {
        return matchedAt;
    }
}
