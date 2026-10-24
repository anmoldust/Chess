package com.ccmmd.chess.matchmakingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * One player waiting for an opponent.
 *
 * <p>The queue lives in the database rather than Redis, deliberately: enqueuing then publishing
 * would otherwise be two writes to two systems that cannot commit together. Keeping the queue in
 * the same database as the outbox means "this player is queued" and "the world was told" commit
 * atomically.
 */
@Entity
@Table(name = "matchmaking_queue")
public class QueueEntry {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "display_name", length = 60)
    private String displayName;

    @Column(name = "time_control", nullable = false, length = 24)
    private String timeControl;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QueueStatus status;

    /** Set when this entry is paired, so the player can be told where to go without a push channel. */
    @Column(name = "match_id", length = 36)
    private String matchId;

    @Version
    private long version;

    protected QueueEntry() {
    }

    public QueueEntry(String id, String userId, String displayName, String timeControl) {
        this.id = id;
        this.userId = userId;
        this.displayName = displayName;
        this.timeControl = timeControl;
        this.queuedAt = Instant.now();
        this.status = QueueStatus.QUEUED;
    }

    public void markMatched(String matchId) {
        this.status = QueueStatus.MATCHED;
        this.matchId = matchId;
    }

    public void cancel() {
        this.status = QueueStatus.CANCELLED;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTimeControl() {
        return timeControl;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public QueueStatus getStatus() {
        return status;
    }

    public String getMatchId() {
        return matchId;
    }
}
