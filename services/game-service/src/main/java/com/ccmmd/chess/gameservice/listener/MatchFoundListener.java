package com.ccmmd.chess.gameservice.listener;

import com.ccmmd.chess.gameservice.config.GameQueueConfig;
import com.ccmmd.chess.gameservice.dto.GameDto;
import com.ccmmd.chess.gameservice.service.GameEventPublisher;
import com.ccmmd.chess.gameservice.service.GameService;
import com.ccmmd.chess.platform.events.EventEnvelope;
import com.ccmmd.chess.platform.events.payload.MatchFound;
import com.ccmmd.chess.platform.messaging.IdempotentEventProcessor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Turns a matchmaking pairing into an actual game.
 *
 * <p>Two layers of safety, and they answer different questions. The idempotency processor stops a
 * redelivered event being handled twice. {@code createMatchedGame} independently refuses to create
 * a game whose id already exists — because a redelivery after a restart has no "processed" record,
 * and the game id is the thing that must be unique, not the event.
 */
@Component
public class MatchFoundListener {

    private static final String CONSUMER = "game-service.matches";

    private final GameService gameService;
    private final GameEventPublisher publisher;
    private final IdempotentEventProcessor processor;

    public MatchFoundListener(
            GameService gameService, GameEventPublisher publisher, IdempotentEventProcessor processor) {
        this.gameService = gameService;
        this.publisher = publisher;
        this.processor = processor;
    }

    @RabbitListener(queues = GameQueueConfig.MATCHES_QUEUE)
    public void onMatchFound(EventEnvelope envelope) {
        AtomicReference<GameDto> created = new AtomicReference<>();

        processor.process(envelope, CONSUMER, MatchFound.class,
                match -> gameService.createMatchedGame(match).ifPresent(created::set));

        GameDto dto = created.get();
        if (dto != null) {
            publisher.gameUpdated(dto);
        }
    }
}
