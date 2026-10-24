package com.ccmmd.chess.platform.events;

import com.ccmmd.chess.platform.events.payload.MovePlayed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventEnvelopeTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private MovePlayed aMove() {
        return new MovePlayed("game-1", 1, "e2e4", "fen-after", "IN_PROGRESS", "user-white");
    }

    @Test
    void wrapsAPayloadWithAnIdAndTimestamp() {
        EventEnvelope envelope = EventEnvelope.of(
                EventType.GAME_MOVE_PLAYED, "game-service", "corr-1", null, aMove(), objectMapper);

        assertThat(envelope.eventId()).isNotBlank();
        assertThat(envelope.type()).isEqualTo(EventType.GAME_MOVE_PLAYED);
        assertThat(envelope.producer()).isEqualTo("game-service");
        assertThat(envelope.correlationId()).isEqualTo("corr-1");
        assertThat(envelope.causationId()).isNull();
        assertThat(envelope.schemaVersion()).isEqualTo(EventEnvelope.CURRENT_SCHEMA_VERSION);
        assertThat(envelope.occurredAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void survivesARoundTripThroughJson() throws Exception {
        EventEnvelope original = EventEnvelope.of(
                EventType.GAME_MOVE_PLAYED, "game-service", "corr-1", "cause-1", aMove(), objectMapper);

        String json = objectMapper.writeValueAsString(original);
        EventEnvelope restored = objectMapper.readValue(json, EventEnvelope.class);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.payloadAs(MovePlayed.class, objectMapper)).isEqualTo(aMove());
    }

    @Test
    void payloadIsCarriedAsAnUntypedTreeSoProducersAndConsumersDecouple() {
        EventEnvelope envelope = EventEnvelope.of(
                EventType.GAME_MOVE_PLAYED, "game-service", null, null, aMove(), objectMapper);

        assertThat(envelope.payload().get("uci").asText()).isEqualTo("e2e4");
    }

    @Test
    void aFollowOnEventCarriesTheTraceForward() {
        EventEnvelope cause = EventEnvelope.of(
                EventType.GAME_ENDED, "game-service", "corr-9", null, aMove(), objectMapper);

        EventEnvelope effect = EventEnvelope.of(
                EventType.ANALYSIS_COMPLETED, "analysis-service", cause.correlationId(), null, aMove(), objectMapper)
                .causedBy(cause);

        assertThat(effect.correlationId()).isEqualTo("corr-9");
        assertThat(effect.causationId()).isEqualTo(cause.eventId());
        assertThat(effect.eventId()).isNotEqualTo(cause.eventId());
    }
}
