package com.ccmmd.chess.presenceservice.listener;

import com.ccmmd.chess.platform.amqp.RabbitNames;
import com.ccmmd.chess.platform.events.EventEnvelope;
import com.ccmmd.chess.platform.events.EventType;
import com.ccmmd.chess.platform.events.payload.MovePlayed;
import com.ccmmd.chess.presenceservice.service.PresenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Marks players as mid-game when they move.
 *
 * <p>No idempotency bookkeeping: this service has no database, and the handler is naturally
 * idempotent — writing the same game id twice is the same as writing it once.
 */
@Configuration
public class PresenceListener {

    public static final String MOVES_QUEUE = "chess.presence-service.moves";
    public static final String MOVES_DLQ = "chess.presence-service.moves.dlq";

    private final PresenceService presence;
    private final ObjectMapper objectMapper;

    public PresenceListener(PresenceService presence, ObjectMapper objectMapper) {
        this.presence = presence;
        this.objectMapper = objectMapper;
    }

    @Bean
    public Queue presenceMovesQueue() {
        return QueueBuilder.durable(MOVES_QUEUE)
                .deadLetterExchange(RabbitNames.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitNames.deadLetterRoutingKey(EventType.GAME_MOVE_PLAYED))
                .build();
    }

    @Bean
    public Queue presenceMovesDeadLetterQueue() {
        return QueueBuilder.durable(MOVES_DLQ).build();
    }

    @Bean
    public Binding presenceMovesBinding(Queue presenceMovesQueue, TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(presenceMovesQueue)
                .to(chessEventsExchange)
                .with(EventType.GAME_MOVE_PLAYED);
    }

    @Bean
    public Binding presenceMovesDlqBinding(Queue presenceMovesDeadLetterQueue, TopicExchange chessDeadLetterExchange) {
        return BindingBuilder.bind(presenceMovesDeadLetterQueue)
                .to(chessDeadLetterExchange)
                .with(RabbitNames.deadLetterRoutingKey(EventType.GAME_MOVE_PLAYED));
    }

    @RabbitListener(queues = MOVES_QUEUE)
    public void onMovePlayed(EventEnvelope envelope) {
        MovePlayed move = envelope.payloadAs(MovePlayed.class, objectMapper);
        presence.markInGame(move.byUserId(), move.gameId());
    }
}
