package com.ccmmd.chess.matchmakingservice.listener;

import com.ccmmd.chess.matchmakingservice.config.MatchmakingQueueConfig;
import com.ccmmd.chess.matchmakingservice.service.RatingService;
import com.ccmmd.chess.platform.events.EventEnvelope;
import com.ccmmd.chess.platform.events.payload.GameEnded;
import com.ccmmd.chess.platform.messaging.IdempotentEventProcessor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Turns a finished game into rating changes.
 *
 * <p>The result arrives as an event rather than being computed here, because only game-service owns
 * a game's outcome; matchmaking just consumes the fact and keeps the two players' numbers current.
 * Consumption goes through {@link IdempotentEventProcessor}, so a redelivered result is applied once.
 */
@Component
public class GameEndedListener {

    private static final String CONSUMER = "matchmaking-service.ratings";

    private final RatingService ratingService;
    private final IdempotentEventProcessor processor;

    public GameEndedListener(RatingService ratingService, IdempotentEventProcessor processor) {
        this.ratingService = ratingService;
        this.processor = processor;
    }

    @RabbitListener(queues = MatchmakingQueueConfig.RATINGS_QUEUE)
    public void onGameEnded(EventEnvelope envelope) {
        processor.process(envelope, CONSUMER, GameEnded.class, ratingService::applyResult);
    }
}
