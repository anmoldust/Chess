package com.ccmmd.chess.gameservice.listener;

import com.ccmmd.chess.gameservice.config.GameQueueConfig;
import com.ccmmd.chess.gameservice.dto.GameDto;
import com.ccmmd.chess.gameservice.service.GameEventPublisher;
import com.ccmmd.chess.gameservice.service.GameService;
import com.ccmmd.chess.platform.events.EventEnvelope;
import com.ccmmd.chess.platform.events.payload.EngineMoveComputed;
import com.ccmmd.chess.platform.messaging.IdempotentEventProcessor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Applies the engine's answer.
 *
 * <p>Guarded twice over, deliberately. The idempotency processor drops a redelivered event, and
 * {@code applyEngineMove} independently refuses a reply that does not match the current position —
 * because "already processed" and "still relevant" are different questions, and a duplicated reply
 * is exactly the case where they diverge.
 */
@Component
public class EngineMoveListener {

    private static final String CONSUMER = "game-service.engine-replies";

    private final GameService gameService;
    private final GameEventPublisher publisher;
    private final IdempotentEventProcessor processor;

    public EngineMoveListener(
            GameService gameService, GameEventPublisher publisher, IdempotentEventProcessor processor) {
        this.gameService = gameService;
        this.publisher = publisher;
        this.processor = processor;
    }

    @RabbitListener(queues = GameQueueConfig.ENGINE_REPLIES_QUEUE)
    public void onEngineMoveComputed(EventEnvelope envelope) {
        AtomicReference<GameDto> updated = new AtomicReference<>();

        boolean handled = processor.process(
                envelope,
                CONSUMER,
                EngineMoveComputed.class,
                reply -> gameService.applyEngineMove(reply).ifPresent(updated::set));

        // Broadcast after the transaction has committed, so a viewer never sees a move the
        // database rolled back.
        GameDto dto = updated.get();
        if (handled && dto != null) {
            publisher.gameUpdated(dto);
        }
    }
}
