package com.ccmmd.chess.platform.messaging;

import com.ccmmd.chess.platform.amqp.RabbitNames;
import com.ccmmd.chess.platform.events.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Drains the outbox to the broker.
 *
 * <p>Delivery is deliberately **at-least-once**: if the process dies between the publish and the
 * {@code published_at} write, the event is sent again. Consumers must therefore be idempotent —
 * see {@link IdempotentEventProcessor}. The alternative (mark-then-publish) loses events, and a
 * lost move notification is worse than a duplicated one.
 *
 * <p>The scheduled method carries {@code @Transactional} itself rather than calling a transactional
 * helper: a self-invocation would bypass the proxy and silently run without a transaction.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OutboxRelay(OutboxRepository outboxRepository, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(
            fixedDelayString = "${ccmmd.messaging.outbox.relay-delay-ms:1000}",
            initialDelayString = "${ccmmd.messaging.outbox.relay-initial-delay-ms:0}")
    @Transactional
    public void relayPending() {
        List<OutboxEntry> pending = outboxRepository.findTop200ByPublishedAtIsNullOrderByOccurredAtAsc();
        if (pending.isEmpty()) {
            return;
        }

        int sent = 0;
        for (OutboxEntry entry : pending) {
            try {
                EventEnvelope envelope = objectMapper.readValue(entry.getEnvelopeJson(), EventEnvelope.class);
                rabbitTemplate.convertAndSend(RabbitNames.EVENTS_EXCHANGE, envelope.type(), envelope);
                entry.markPublished();
                sent += 1;
            } catch (Exception failure) {
                // One bad event must not block the rest of the batch; it stays unpublished and is
                // retried on the next tick with its error recorded.
                entry.recordFailure(failure);
                log.warn("outbox: could not publish {} ({})", entry.getEventId(), entry.getType(), failure);
            }
        }

        outboxRepository.saveAll(pending);
        log.debug("outbox: published {} of {} pending event(s)", sent, pending.size());
    }
}
