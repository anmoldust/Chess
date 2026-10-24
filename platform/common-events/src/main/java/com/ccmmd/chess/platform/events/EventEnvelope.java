package com.ccmmd.chess.platform.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The envelope every message on the bus is wrapped in.
 *
 * <p>The payload is carried as a {@link JsonNode} rather than a typed field so producers and
 * consumers can evolve independently: a producer serialises its record once, and a consumer
 * materialises it with {@link #payloadAs(Class, ObjectMapper)}. That keeps this module free of
 * any dependency on a service's domain types.
 *
 * @param eventId       unique per event; consumers deduplicate on it (delivery is at-least-once)
 * @param type          one of {@link EventType}
 * @param schemaVersion bumped on a breaking payload change
 * @param occurredAt    when the producer's transaction committed the fact
 * @param producer      service name that emitted it, for tracing
 * @param correlationId one id for the whole user action, propagated across every hop
 * @param causationId   the {@code eventId} that caused this one, or null for an originating event
 * @param payload       the event body
 */
public record EventEnvelope(
        String eventId,
        String type,
        int schemaVersion,
        Instant occurredAt,
        String producer,
        String correlationId,
        String causationId,
        JsonNode payload
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static EventEnvelope of(
            String type,
            String producer,
            String correlationId,
            String causationId,
            Object payload,
            ObjectMapper objectMapper) {
        return new EventEnvelope(
                UUID.randomUUID().toString(),
                Objects.requireNonNull(type, "type"),
                CURRENT_SCHEMA_VERSION,
                Instant.now(),
                Objects.requireNonNull(producer, "producer"),
                correlationId,
                causationId,
                objectMapper.valueToTree(payload));
    }

    /** A copy of this envelope representing a follow-on event, carrying the trace forward. */
    public EventEnvelope causedBy(EventEnvelope cause) {
        return new EventEnvelope(
                UUID.randomUUID().toString(),
                type,
                schemaVersion,
                Instant.now(),
                producer,
                cause.correlationId(),
                cause.eventId(),
                payload);
    }

    public <T> T payloadAs(Class<T> payloadType, ObjectMapper objectMapper) {
        return objectMapper.convertValue(payload, payloadType);
    }
}
