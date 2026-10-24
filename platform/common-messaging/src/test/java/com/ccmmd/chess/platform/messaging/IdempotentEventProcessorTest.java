package com.ccmmd.chess.platform.messaging;

import com.ccmmd.chess.platform.events.EventEnvelope;
import com.ccmmd.chess.platform.events.EventType;
import com.ccmmd.chess.platform.events.payload.GameEnded;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.show-sql=false")
@EntityScan(basePackages = "com.ccmmd.chess.platform.messaging")
@EnableJpaRepositories(basePackages = "com.ccmmd.chess.platform.messaging")
@ContextConfiguration(classes = MessagingTestApplication.class)
class IdempotentEventProcessorTest {

    @Autowired
    private ProcessedEventRepository processedEvents;

    private IdempotentEventProcessor processor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        processor = new IdempotentEventProcessor(processedEvents, objectMapper);
    }

    private EventEnvelope event() {
        return EventEnvelope.of(
                EventType.GAME_ENDED,
                "game-service",
                "corr-1",
                null,
                new GameEnded("game-1", "WHITE_WON", "1-0", 12, "user-white", "user-black"),
                objectMapper);
    }

    @Test
    @DisplayName("a redelivered event is handled once")
    void handlesEachEventOnce() {
        EventEnvelope envelope = event();
        List<String> handled = new ArrayList<>();

        boolean first = processor.process(envelope, "notification-service", GameEnded.class,
                payload -> handled.add(payload.gameId()));
        boolean second = processor.process(envelope, "notification-service", GameEnded.class,
                payload -> handled.add(payload.gameId()));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(handled).containsExactly("game-1");
    }

    @Test
    @DisplayName("the same event is handled independently by each consumer")
    void eachConsumerHasItsOwnDedupe() {
        EventEnvelope envelope = event();

        assertThat(processor.process(envelope, "notification-service", GameEnded.class, payload -> { })).isTrue();
        assertThat(processor.process(envelope, "analysis-service", GameEnded.class, payload -> { })).isTrue();
    }

    @Test
    @DisplayName("a handler failure is not recorded as processed, so the broker retries it")
    void aFailedHandlerIsRetriedRatherThanSwallowed() {
        EventEnvelope envelope = event();

        assertThatThrownBy(() -> processor.process(envelope, "notification-service", GameEnded.class,
                payload -> {
                    throw new IllegalStateException("downstream is down");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(processedEvents.existsByConsumerAndEventId("notification-service", envelope.eventId())).isFalse();
    }

    @Test
    @DisplayName("the payload is materialised into its record type")
    void convertsThePayload() {
        List<String> results = new ArrayList<>();

        processor.process(event(), "analysis-service", GameEnded.class, payload -> results.add(payload.result()));

        assertThat(results).containsExactly("1-0");
    }
}
