package com.ccmmd.chess.platform.messaging;

import com.ccmmd.chess.platform.events.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

/**
 * Runs an event handler at most once per consumer.
 *
 * <p>The payload conversion, the handler, and the "already processed" marker all happen in ONE
 * transaction. If the handler throws, the marker rolls back too and the broker redelivers — so a
 * failure is retried rather than silently swallowed, and a success is never applied twice.
 */
@Component
public class IdempotentEventProcessor {

    private final ProcessedEventRepository processedEvents;
    private final ObjectMapper objectMapper;

    public IdempotentEventProcessor(ProcessedEventRepository processedEvents, ObjectMapper objectMapper) {
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    /**
     * @return true when this call handled the event, false when it had already been handled
     */
    @Transactional
    public <T> boolean process(EventEnvelope envelope, String consumer, Class<T> payloadType, Consumer<T> handler) {
        if (processedEvents.existsByConsumerAndEventId(consumer, envelope.eventId())) {
            return false;
        }

        handler.accept(envelope.payloadAs(payloadType, objectMapper));
        processedEvents.save(new ProcessedEvent(consumer, envelope.eventId()));
        return true;
    }
}
