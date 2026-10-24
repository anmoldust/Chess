package com.ccmmd.chess.engineservice.listener;

import com.ccmmd.chess.engineservice.config.EngineQueueConfig;
import com.ccmmd.chess.engineservice.engine.EngineMoveCalculator;
import com.ccmmd.chess.engineservice.engine.EngineMoveResult;
import com.ccmmd.chess.platform.amqp.RabbitNames;
import com.ccmmd.chess.platform.events.EventEnvelope;
import com.ccmmd.chess.platform.events.EventType;
import com.ccmmd.chess.platform.events.payload.EngineMoveComputed;
import com.ccmmd.chess.platform.events.payload.EngineMoveRequested;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Answers {@code engine.move.requested} with {@code engine.move.computed}.
 *
 * <p>There is no outbox here, on purpose. The outbox exists to avoid a dual write — state and the
 * announcement of it committing separately. This service commits no state: it computes and replies.
 * If the reply fails to publish, the listener throws, the message is not acknowledged, and the
 * request is redelivered and recomputed.
 *
 * <p>An engine that cannot produce a move still replies, carrying the failure as a value. That
 * keeps a broken engine from being retried forever; a genuinely malformed message, by contrast,
 * is rejected and lands in the dead-letter queue.
 */
@Component
public class EngineMoveListener {

    private static final Logger log = LoggerFactory.getLogger(EngineMoveListener.class);
    private static final String PRODUCER = "engine-service";

    private final EngineMoveCalculator calculator;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public EngineMoveListener(
            EngineMoveCalculator calculator, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.calculator = calculator;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = EngineQueueConfig.REQUESTS_QUEUE)
    public void onMoveRequested(EventEnvelope envelope) {
        EngineMoveRequested request = envelope.payloadAs(EngineMoveRequested.class, objectMapper);

        long startedAt = System.nanoTime();
        EngineMoveResult result = calculator.compute(request.fen(), request.difficulty());
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        if (result.failed()) {
            log.warn("engine failed for game {} after {} ms: {}", request.gameId(), elapsedMs, result.failureReason());
        } else {
            log.info("engine answered {} for game {} in {} ms", result.uci(), request.gameId(), elapsedMs);
        }

        EngineMoveComputed reply = new EngineMoveComputed(
                request.correlationId(),
                request.gameId(),
                result.uci(),
                null,
                result.failed(),
                result.failureReason());

        EventEnvelope response = EventEnvelope.of(
                EventType.ENGINE_MOVE_COMPUTED,
                PRODUCER,
                envelope.correlationId(),
                envelope.eventId(),
                reply,
                objectMapper);

        // Published before the listener returns, so a publish failure means no ack and a retry.
        rabbitTemplate.convertAndSend(
                RabbitNames.EVENTS_EXCHANGE, EventType.ENGINE_MOVE_COMPUTED, response);
    }
}
