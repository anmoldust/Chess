package com.ccmmd.chess.matchmakingservice.config;

import com.ccmmd.chess.platform.amqp.RabbitNames;
import com.ccmmd.chess.platform.events.EventType;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The inbox for finished games, with a dead-letter path so a poison result is not retried forever.
 *
 * <p>This is matchmaking's rating feed: it turns the authoritative game result into a rating change.
 * It is a separate queue from notification-service's and analysis-service's, so each consumer owns
 * its own retry and its own poison-message trail.
 */
@Configuration
public class MatchmakingQueueConfig {

    public static final String RATINGS_QUEUE = "chess.matchmaking-service.ratings";
    public static final String RATINGS_DLQ = "chess.matchmaking-service.ratings.dlq";

    @Bean
    public Queue gameEndedQueue() {
        return QueueBuilder.durable(RATINGS_QUEUE)
                .deadLetterExchange(RabbitNames.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitNames.deadLetterRoutingKey(EventType.GAME_ENDED))
                .build();
    }

    @Bean
    public Queue gameEndedDeadLetterQueue() {
        return QueueBuilder.durable(RATINGS_DLQ).build();
    }

    @Bean
    public Binding gameEndedBinding(Queue gameEndedQueue, TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(gameEndedQueue)
                .to(chessEventsExchange)
                .with(EventType.GAME_ENDED);
    }

    /** Scoped to this queue's own event, so a poison result lands only in this service's DLQ. */
    @Bean
    public Binding gameEndedDlqBinding(Queue gameEndedDeadLetterQueue, TopicExchange chessDeadLetterExchange) {
        return BindingBuilder.bind(gameEndedDeadLetterQueue)
                .to(chessDeadLetterExchange)
                .with(RabbitNames.deadLetterRoutingKey(EventType.GAME_ENDED));
    }
}
