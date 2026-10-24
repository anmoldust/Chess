package com.ccmmd.chess.notificationservice.listener;

import com.ccmmd.chess.notificationservice.service.NotificationService;
import com.ccmmd.chess.platform.amqp.RabbitNames;
import com.ccmmd.chess.platform.events.EventEnvelope;
import com.ccmmd.chess.platform.events.EventType;
import com.ccmmd.chess.platform.events.payload.GameEnded;
import com.ccmmd.chess.platform.events.payload.MatchFound;
import com.ccmmd.chess.platform.events.payload.UserRegistered;
import com.ccmmd.chess.platform.messaging.IdempotentEventProcessor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Turns interesting domain events into something a person can read.
 *
 * <p>One queue, three routing keys — so ordering between kinds is preserved and there is a single
 * dead-letter path. Every handler goes through {@link IdempotentEventProcessor}: delivery is
 * at-least-once, and nobody wants to be congratulated twice on the same win.
 */
@Configuration
public class NotificationListener {

    public static final String EVENTS_QUEUE = "chess.notification-service";
    public static final String EVENTS_DLQ = "chess.notification-service.dlq";
    private static final String CONSUMER = "notification-service";

    private final NotificationService notifications;
    private final IdempotentEventProcessor processor;

    public NotificationListener(NotificationService notifications, IdempotentEventProcessor processor) {
        this.notifications = notifications;
        this.processor = processor;
    }

    @Bean
    public Queue notificationEventsQueue() {
        return QueueBuilder.durable(EVENTS_QUEUE)
                .deadLetterExchange(RabbitNames.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitNames.deadLetterRoutingKey("notification.events"))
                .build();
    }

    @Bean
    public Queue notificationEventsDeadLetterQueue() {
        return QueueBuilder.durable(EVENTS_DLQ).build();
    }

    @Bean
    public Binding notificationUserRegisteredBinding(Queue notificationEventsQueue, TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue)
                .to(chessEventsExchange).with(EventType.USER_REGISTERED);
    }

    @Bean
    public Binding notificationMatchFoundBinding(Queue notificationEventsQueue, TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue)
                .to(chessEventsExchange).with(EventType.MATCH_FOUND);
    }

    @Bean
    public Binding notificationGameEndedBinding(Queue notificationEventsQueue, TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue)
                .to(chessEventsExchange).with(EventType.GAME_ENDED);
    }

    @Bean
    public Binding notificationEventsDlqBinding(
            Queue notificationEventsDeadLetterQueue, TopicExchange chessDeadLetterExchange) {
        return BindingBuilder.bind(notificationEventsDeadLetterQueue)
                .to(chessDeadLetterExchange)
                .with(RabbitNames.deadLetterRoutingKey("notification.events"));
    }

    @RabbitListener(queues = EVENTS_QUEUE)
    public void onEvent(EventEnvelope envelope) {
        switch (envelope.type()) {
            case EventType.USER_REGISTERED -> processor.process(
                    envelope, CONSUMER, UserRegistered.class,
                    event -> notifications.record(
                            event.userId(), "WELCOME",
                            "Welcome to CCMMD, " + event.displayName() + "."));

            case EventType.MATCH_FOUND -> processor.process(
                    envelope, CONSUMER, MatchFound.class,
                    match -> {
                        notifications.record(
                                match.whiteUserId(), "MATCH_FOUND",
                                "Your match is ready with " + match.blackDisplayName() + ".");
                        notifications.record(
                                match.blackUserId(), "MATCH_FOUND",
                                "Your match is ready with " + match.whiteDisplayName() + ".");
                    });

            case EventType.GAME_ENDED -> processor.process(
                    envelope, CONSUMER, GameEnded.class,
                    ended -> {
                        if (ended.whiteUserId() != null) {
                            notifications.record(
                                    ended.whiteUserId(), "GAME_ENDED", "Game finished: " + ended.result() + ".");
                        }
                        if (ended.blackUserId() != null) {
                            notifications.record(
                                    ended.blackUserId(), "GAME_ENDED", "Game finished: " + ended.result() + ".");
                        }
                    });

            // Not our business. Ignored rather than dead-lettered: another service's event
            // arriving here is not an error.
            default -> {
            }
        }
    }
}
