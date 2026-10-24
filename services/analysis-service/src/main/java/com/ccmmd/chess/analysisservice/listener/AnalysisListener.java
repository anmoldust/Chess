package com.ccmmd.chess.analysisservice.listener;

import com.ccmmd.chess.analysisservice.analysis.GameClient;
import com.ccmmd.chess.analysisservice.service.AnalysisService;
import com.ccmmd.chess.platform.amqp.RabbitNames;
import com.ccmmd.chess.platform.events.EventEnvelope;
import com.ccmmd.chess.platform.events.EventType;
import com.ccmmd.chess.platform.events.payload.GameEnded;
import com.ccmmd.chess.platform.messaging.IdempotentEventProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Analyses each finished game.
 *
 * <p>The event carries only the outcome, so the move list is fetched from game-service. That is a
 * deliberate trade: a fat event would mean every consumer pays for a payload only this service
 * needs.
 *
 * <p>If the fetch fails the listener throws, the message is not acknowledged, and the broker
 * redelivers — which is the right behaviour for a transient game-service outage. A permanently
 * missing game would retry three times and then land in the dead-letter queue.
 */
@Configuration
public class AnalysisListener {

    private static final Logger log = LoggerFactory.getLogger(AnalysisListener.class);

    public static final String ENDED_QUEUE = "chess.analysis-service.ended";
    public static final String ENDED_DLQ = "chess.analysis-service.ended.dlq";
    private static final String CONSUMER = "analysis-service";

    private final AnalysisService analysisService;
    private final GameClient gameClient;
    private final IdempotentEventProcessor processor;

    public AnalysisListener(
            AnalysisService analysisService, GameClient gameClient, IdempotentEventProcessor processor) {
        this.analysisService = analysisService;
        this.gameClient = gameClient;
        this.processor = processor;
    }

    @Bean
    public Queue analysisEndedQueue() {
        return QueueBuilder.durable(ENDED_QUEUE)
                .deadLetterExchange(RabbitNames.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitNames.deadLetterRoutingKey(EventType.GAME_ENDED))
                .build();
    }

    @Bean
    public Queue analysisEndedDeadLetterQueue() {
        return QueueBuilder.durable(ENDED_DLQ).build();
    }

    @Bean
    public Binding analysisEndedBinding(Queue analysisEndedQueue, TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(analysisEndedQueue)
                .to(chessEventsExchange).with(EventType.GAME_ENDED);
    }

    @Bean
    public Binding analysisEndedDlqBinding(Queue analysisEndedDeadLetterQueue, TopicExchange chessDeadLetterExchange) {
        return BindingBuilder.bind(analysisEndedDeadLetterQueue)
                .to(chessDeadLetterExchange)
                .with(RabbitNames.deadLetterRoutingKey(EventType.GAME_ENDED));
    }

    @RabbitListener(queues = ENDED_QUEUE)
    public void onGameEnded(EventEnvelope envelope) {
        processor.process(envelope, CONSUMER, GameEnded.class, ended -> {
            if (ended.whiteUserId() == null && ended.blackUserId() == null) {
                // A game against the computer has one human; still worth analysing.
                log.debug("analysing game {} with no human seats", ended.gameId());
            }
            gameClient.fetch(ended.gameId())
                    .map(analysisService::analyse)
                    .block();
        });
    }
}
