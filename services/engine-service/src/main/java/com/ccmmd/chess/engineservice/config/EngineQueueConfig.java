package com.ccmmd.chess.engineservice.config;

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
 * The engine's inbox and its dead-letter path.
 *
 * <p>A queue without a DLX turns a poison message into an infinite redelivery loop. The binding
 * to {@code chess.dlx} is what makes a malformed request observable (and inspectable) rather than
 * a spinning consumer.
 */
@Configuration
public class EngineQueueConfig {

    public static final String REQUESTS_QUEUE = "chess.engine-service";
    public static final String REQUESTS_DLQ = "chess.engine-service.dlq";

    @Bean
    public Queue engineRequestsQueue() {
        return QueueBuilder.durable(REQUESTS_QUEUE)
                .deadLetterExchange(RabbitNames.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitNames.deadLetterRoutingKey(EventType.ENGINE_MOVE_REQUESTED))
                .build();
    }

    @Bean
    public Queue engineRequestsDeadLetterQueue() {
        return QueueBuilder.durable(REQUESTS_DLQ).build();
    }

    @Bean
    public Binding engineRequestsBinding(Queue engineRequestsQueue, TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(engineRequestsQueue)
                .to(chessEventsExchange)
                .with(EventType.ENGINE_MOVE_REQUESTED);
    }

    /**
     * Bound to a routing key specific to this queue's event, not to {@code #}. A catch-all binding
     * means every service's DLQ receives every other service's poison messages, which turns a
     * precise alert into noise.
     */
    @Bean
    public Binding engineRequestsDlqBinding(Queue engineRequestsDeadLetterQueue, TopicExchange chessDeadLetterExchange) {
        return BindingBuilder.bind(engineRequestsDeadLetterQueue)
                .to(chessDeadLetterExchange)
                .with(RabbitNames.deadLetterRoutingKey(EventType.ENGINE_MOVE_REQUESTED));
    }
}
