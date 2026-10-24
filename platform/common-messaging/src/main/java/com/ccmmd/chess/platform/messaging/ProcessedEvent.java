package com.ccmmd.chess.platform.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Records that a given consumer has already handled a given event. The unique constraint is the
 * mechanism: two concurrent deliveries race, one insert wins, the loser's transaction rolls back
 * and its effects with it.
 */
@Entity
@Table(
        name = "processed_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_processed_event_consumer_event",
                columnNames = {"consumer", "event_id"}))
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String consumer;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(String consumer, String eventId) {
        this.consumer = consumer;
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getConsumer() {
        return consumer;
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
