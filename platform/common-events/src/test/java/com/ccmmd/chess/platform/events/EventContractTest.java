package com.ccmmd.chess.platform.events;

import com.ccmmd.chess.platform.events.payload.AnalysisCompleted;
import com.ccmmd.chess.platform.events.payload.EngineMoveComputed;
import com.ccmmd.chess.platform.events.payload.EngineMoveRequested;
import com.ccmmd.chess.platform.events.payload.GameCreated;
import com.ccmmd.chess.platform.events.payload.GameEnded;
import com.ccmmd.chess.platform.events.payload.MatchFound;
import com.ccmmd.chess.platform.events.payload.MovePlayed;
import com.ccmmd.chess.platform.events.payload.NotificationSent;
import com.ccmmd.chess.platform.events.payload.PlayerQueued;
import com.ccmmd.chess.platform.events.payload.PresenceUpdated;
import com.ccmmd.chess.platform.events.payload.UserRegistered;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The event payloads are a contract between services that are deployed separately, so a renamed
 * field is a breaking change even though it compiles. These tests pin the wire shape: if a record
 * field is added, removed or renamed, this fails instead of a consumer failing in production.
 *
 * <p>When you intend to change a payload, change this test too — and remember consumers must be
 * able to tolerate an added field before producers start sending it.
 */
class EventContractTest {

    private final ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    private Set<String> wireFields(Object payload) {
        JsonNode json = mapper.valueToTree(payload);
        Set<String> names = new TreeSet<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    @DisplayName("user.registered")
    void userRegistered() {
        assertThat(wireFields(new UserRegistered("u1", "a@b.c", "Ada", Instant.EPOCH)))
                .containsExactlyInAnyOrder("userId", "email", "displayName", "registeredAt");
    }

    @Test
    @DisplayName("game.created")
    void gameCreated() {
        assertThat(wireFields(new GameCreated("g1", "HUMAN_VS_HUMAN", "w", "b", null, Instant.EPOCH)))
                .containsExactlyInAnyOrder("gameId", "mode", "whiteUserId", "blackUserId", "aiColor", "createdAt");
    }

    @Test
    @DisplayName("game.move.played")
    void movePlayed() {
        assertThat(wireFields(new MovePlayed("g1", 1, "e2e4", "fen", "IN_PROGRESS", "u1")))
                .containsExactlyInAnyOrder("gameId", "ply", "uci", "fen", "status", "byUserId");
    }

    @Test
    @DisplayName("game.ended")
    void gameEnded() {
        assertThat(wireFields(new GameEnded("g1", "WHITE_WON", "1-0", 12, "w", "b")))
                .containsExactlyInAnyOrder("gameId", "status", "result", "plyCount", "whiteUserId", "blackUserId");
    }

    @Test
    @DisplayName("engine.move.requested — the request half of the only request/reply pair")
    void engineMoveRequested() {
        assertThat(wireFields(new EngineMoveRequested("g1", "fen", "MEDIUM", "g1:2")))
                .containsExactlyInAnyOrder("gameId", "fen", "difficulty", "correlationId");
    }

    @Test
    @DisplayName("engine.move.computed")
    void engineMoveComputed() {
        assertThat(wireFields(new EngineMoveComputed("g1:2", "g1", "e7e5", null, false, null)))
                .containsExactlyInAnyOrder("correlationId", "gameId", "uci", "ponder", "failed", "failureReason");
    }

    @Test
    @DisplayName("matchmaking.match.found — game-service depends on every field here")
    void matchFound() {
        assertThat(wireFields(new MatchFound("m1", "g1", "w", "b", "White", "Black", "BLITZ", Instant.EPOCH)))
                .containsExactlyInAnyOrder("matchId", "gameId", "whiteUserId", "blackUserId",
                        "whiteDisplayName", "blackDisplayName", "timeControl", "matchedAt");
    }

    @Test
    @DisplayName("player.queued")
    void playerQueued() {
        assertThat(wireFields(new PlayerQueued("u1", "Ada", "BLITZ", Instant.EPOCH)))
                .containsExactlyInAnyOrder("userId", "displayName", "timeControl", "queuedAt");
    }

    @Test
    @DisplayName("presence.updated")
    void presenceUpdated() {
        assertThat(wireFields(new PresenceUpdated("u1", true, "g1", Instant.EPOCH)))
                .containsExactlyInAnyOrder("userId", "online", "gameId", "at");
    }

    @Test
    @DisplayName("notification.sent")
    void notificationSent() {
        assertThat(wireFields(new NotificationSent("n1", "u1", "WELCOME", "hi", Instant.EPOCH)))
                .containsExactlyInAnyOrder("notificationId", "userId", "kind", "message", "at");
    }

    @Test
    @DisplayName("analysis.completed")
    void analysisCompleted() {
        assertThat(wireFields(new AnalysisCompleted("g1", 80.0, 75.0, 1, 2, Instant.EPOCH)))
                .containsExactlyInAnyOrder("gameId", "accuracyWhite", "accuracyBlack",
                        "blundersWhite", "blundersBlack", "completedAt");
    }

    @Test
    @DisplayName("every event type has a dotted routing key")
    void eventTypesLookLikeRoutingKeys() {
        assertThat(EventType.USER_REGISTERED).isEqualTo("user.registered");
        assertThat(EventType.MATCH_FOUND).isEqualTo("matchmaking.match.found");
        assertThat(EventType.ENGINE_MOVE_REQUESTED).isEqualTo("engine.move.requested");
    }
}
