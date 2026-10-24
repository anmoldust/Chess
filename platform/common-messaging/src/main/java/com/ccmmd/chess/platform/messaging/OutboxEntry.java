package com.ccmmd.chess.platform.messaging;

import com.ccmmd.chess.platform.events.EventEnvelope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A row written in the same transaction as the business change it describes. The relay sends it
 * afterwards. This is what removes the dual-write problem: we never have to make a database
 * commit and a broker publish succeed together.
 */
@Entity
@Table(name = "outbox", indexes = @Index(name = "ix_outbox_unpublished", columnList = "published_at"))
public class OutboxEntry {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String type;

    @Column(name = "envelope_json", nullable = false, columnDefinition = "text")
    private String envelopeJson;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    protected OutboxEntry() {
    }

    public OutboxEntry(EventEnvelope envelope, String envelopeJson) {
        this.id = envelope.eventId();
        this.eventId = envelope.eventId();
        this.type = envelope.type();
        this.envelopeJson = envelopeJson;
        this.occurredAt = envelope.occurredAt();
        this.attempts = 0;
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
        this.attempts += 1;
        this.lastError = null;
    }

    public void recordFailure(Exception failure) {
        this.attempts += 1;
        String message = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        this.lastError = message.length() > 500 ? message.substring(0, 500) : message;
    }

    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getType() {
        return type;
    }

    public String getEnvelopeJson() {
        return envelopeJson;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
