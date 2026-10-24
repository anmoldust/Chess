package com.ccmmd.chess.platform.messaging;

import com.ccmmd.chess.platform.amqp.RabbitNames;
import com.ccmmd.chess.platform.events.EventEnvelope;
import com.ccmmd.chess.platform.events.payload.MovePlayed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest(properties = "spring.jpa.show-sql=false")
@EntityScan(basePackages = "com.ccmmd.chess.platform.messaging")
@EnableJpaRepositories(basePackages = "com.ccmmd.chess.platform.messaging")
@ContextConfiguration(classes = MessagingTestApplication.class)
class OutboxRelayTest {

    @Autowired
    private OutboxRepository outboxRepository;

    private RabbitTemplate rabbitTemplate;
    private ObjectMapper objectMapper;
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        rabbitTemplate = mock(RabbitTemplate.class);
        relay = new OutboxRelay(outboxRepository, rabbitTemplate, objectMapper);
    }

    private EventEnvelope storePending(String type) throws Exception {
        EventEnvelope envelope = EventEnvelope.of(
                type,
                "test-producer",
                "corr-1",
                null,
                new MovePlayed("game-1", 1, "e2e4", "fen", "IN_PROGRESS", "user-1"),
                objectMapper);
        outboxRepository.save(new OutboxEntry(envelope, objectMapper.writeValueAsString(envelope)));
        return envelope;
    }

    @Test
    @DisplayName("pending events are published and marked sent")
    void publishesPendingEvents() throws Exception {
        storePending("game.created");
        storePending("game.move.played");

        relay.relayPending();

        verify(rabbitTemplate, times(2)).convertAndSend(eq(RabbitNames.EVENTS_EXCHANGE), any(String.class), any(Object.class));
        assertThat(outboxRepository.countByPublishedAtIsNull()).isZero();
    }

    @Test
    @DisplayName("the routing key is the event type")
    void routesOnEventType() throws Exception {
        storePending("game.move.played");

        relay.relayPending();

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitNames.EVENTS_EXCHANGE), eq("game.move.played"), (Object) any(EventEnvelope.class));
    }

    @Test
    @DisplayName("a broker failure leaves the event pending with its error recorded")
    void keepsEventPendingWhenTheBrokerIsDown() throws Exception {
        storePending("game.ended");
        doThrow(new RuntimeException("connection refused"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));

        relay.relayPending();

        assertThat(outboxRepository.countByPublishedAtIsNull()).isEqualTo(1);
        OutboxEntry entry = outboxRepository.findAll().get(0);
        assertThat(entry.getAttempts()).isEqualTo(1);
        assertThat(entry.getLastError()).contains("connection refused");
    }

    @Test
    @DisplayName("a failed event is retried on the next tick, not dropped")
    void retriesOnTheNextTick() throws Exception {
        storePending("game.ended");
        doThrow(new RuntimeException("connection refused"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));
        relay.relayPending();

        org.mockito.Mockito.reset(rabbitTemplate);
        relay.relayPending();

        assertThat(outboxRepository.countByPublishedAtIsNull()).isZero();
        verify(rabbitTemplate).convertAndSend(eq(RabbitNames.EVENTS_EXCHANGE), eq("game.ended"), (Object) any(EventEnvelope.class));
    }

    @Test
    @DisplayName("an empty outbox is a no-op")
    void doesNothingWhenEmpty() {
        relay.relayPending();

        verify(rabbitTemplate, times(0)).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }
}
