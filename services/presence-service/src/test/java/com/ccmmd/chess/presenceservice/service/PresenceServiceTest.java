package com.ccmmd.chess.presenceservice.service;

import com.ccmmd.chess.presenceservice.dto.PresenceDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test: presence state lives in Redis, so it needs Redis running. Using the real store
 * matters — the TTL semantics and the "heartbeat preserves the in-game marker" behaviour are Redis
 * behaviour, not application behaviour.
 */
@SpringBootTest
class PresenceServiceTest {

    @Autowired
    private PresenceService presence;

    private static String user() {
        return UUID.randomUUID().toString();
    }

    @Test
    @DisplayName("a heartbeat marks the player online")
    void heartbeatMarksOnline() {
        PresenceDto status = presence.heartbeat(user());
        assertThat(status.online()).isTrue();
        assertThat(status.gameId()).isNull();
    }

    @Test
    @DisplayName("a heartbeat does not erase the in-game marker")
    void heartbeatPreservesInGameMarker() {
        String userId = user();
        presence.heartbeat(userId);
        presence.markInGame(userId, "game-1");

        PresenceDto status = presence.heartbeat(userId);

        assertThat(status.online()).isTrue();
        assertThat(status.gameId()).isEqualTo("game-1");
    }

    @Test
    @DisplayName("moving marks the player as mid-game")
    void markInGameIsVisible() {
        String userId = user();
        presence.markInGame(userId, "game-2");

        PresenceDto status = presence.status(userId);

        assertThat(status.online()).isTrue();
        assertThat(status.gameId()).isEqualTo("game-2");
    }

    @Test
    @DisplayName("signing out clears presence")
    void offlineClearsPresence() {
        String userId = user();
        presence.heartbeat(userId);

        presence.offline(userId);

        assertThat(presence.status(userId).online()).isFalse();
    }

    @Test
    @DisplayName("a player who never checked in is offline")
    void unknownPlayerIsOffline() {
        assertThat(presence.status(user()).online()).isFalse();
    }

    @Test
    @DisplayName("an anonymous caller cannot heartbeat")
    void anonymousRejected() {
        assertThatThrownBy(() -> presence.heartbeat(null))
                .isInstanceOf(AccessDeniedException.class);
    }
}
