package com.ccmmd.chess.gameservice.config;

import com.ccmmd.chess.platform.amqp.RabbitNames;
import com.ccmmd.chess.platform.events.EventType;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The inbox for engine replies, with a dead-letter path so a poison reply is not retried forever. */
@Configuration
public class GameQueueConfig {

    public static final String ENGINE_REPLIES_QUEUE = "chess.game-service.engine-replies";
    public static final String ENGINE_REPLIES_DLQ = "chess.game-service.engine-replies.dlq";

    public static final String MATCHES_QUEUE = "chess.game-service.matches";
    public static final String MATCHES_DLQ = "chess.game-service.matches.dlq";

    @Bean
    public Queue matchesQueue() {
        return QueueBuilder.durable(MATCHES_QUEUE)
                .deadLetterExchange(RabbitNames.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitNames.deadLetterRoutingKey(EventType.MATCH_FOUND))
                .build();
    }

    @Bean
    public Queue matchesDeadLetterQueue() {
        return QueueBuilder.durable(MATCHES_DLQ).build();
    }

    @Bean
    public Binding matchesBinding(Queue matchesQueue, TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(matchesQueue)
                .to(chessEventsExchange)
                .with(EventType.MATCH_FOUND);
    }

    @Bean
    public Binding matchesDlqBinding(Queue matchesDeadLetterQueue, TopicExchange chessDeadLetterExchange) {
        return BindingBuilder.bind(matchesDeadLetterQueue)
                .to(chessDeadLetterExchange)
                .with(RabbitNames.deadLetterRoutingKey(EventType.MATCH_FOUND));
    }

    @Bean
    public Queue engineRepliesQueue() {
        return QueueBuilder.durable(ENGINE_REPLIES_QUEUE)
                .deadLetterExchange(RabbitNames.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitNames.deadLetterRoutingKey(EventType.ENGINE_MOVE_COMPUTED))
                .build();
    }

    @Bean
    public Queue engineRepliesDeadLetterQueue() {
        return QueueBuilder.durable(ENGINE_REPLIES_DLQ).build();
    }

    @Bean
    public Binding engineRepliesBinding(Queue engineRepliesQueue, TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(engineRepliesQueue)
                .to(chessEventsExchange)
                .with(EventType.ENGINE_MOVE_COMPUTED);
    }

    /** Scoped to this queue's own event, so one service's poison message does not fan out. */
    @Bean
    public Binding engineRepliesDlqBinding(Queue engineRepliesDeadLetterQueue, TopicExchange chessDeadLetterExchange) {
        return BindingBuilder.bind(engineRepliesDeadLetterQueue)
                .to(chessDeadLetterExchange)
                .with(RabbitNames.deadLetterRoutingKey(EventType.ENGINE_MOVE_COMPUTED));
    }
}
