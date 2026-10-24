package com.ccmmd.chess.platform.messaging;

import com.ccmmd.chess.platform.events.EventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * How services announce facts. Deliberately {@link Propagation#MANDATORY}: publishing outside a
 * transaction would let the event be sent for a change that then rolls back, which is the exact
 * inconsistency the outbox exists to prevent. Call this from inside your business transaction.
 */
@Component
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public EventEnvelope publish(
            String type,
            String producer,
            String correlationId,
            String causationId,
            Object payload) {
        EventEnvelope envelope = EventEnvelope.of(type, producer, correlationId, causationId, payload, objectMapper);
        outboxRepository.save(new OutboxEntry(envelope, serialise(envelope)));
        return envelope;
    }

    private String serialise(EventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise event " + envelope.type(), e);
        }
    }
}
