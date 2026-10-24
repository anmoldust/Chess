package com.ccmmd.chess.matchmakingservice.service;

import com.ccmmd.chess.matchmakingservice.domain.QueueEntry;
import com.ccmmd.chess.matchmakingservice.domain.QueueStatus;
import com.ccmmd.chess.matchmakingservice.dto.MatchmakingStatusDto;
import com.ccmmd.chess.matchmakingservice.repository.MatchRepository;
import com.ccmmd.chess.matchmakingservice.repository.QueueRepository;
import com.ccmmd.chess.platform.events.EventType;
import com.ccmmd.chess.platform.messaging.OutboxEntry;
import com.ccmmd.chess.platform.messaging.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rolls back after each test. That is not just tidiness: the queue is shared state, so without it
 * a leftover waiting player from one test gets paired by the next one's matcher call, and the
 * count-based assertions become meaningless. It also supplies the transaction that {@code pairOnce}
 * needs for its row locks.
 */
@SpringBootTest
@org.springframework.transaction.annotation.Transactional
class MatchmakingServiceTest {

    @Autowired
    private MatchmakingService matchmaking;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private OutboxRepository outbox;

    /** Account ids are UUIDs (36 chars), which is what the column is sized for. */
    private static String user() {
        return UUID.randomUUID().toString();
    }

    private java.util.List<String> eventTypes() {
        return outbox.findAll().stream().map(OutboxEntry::getType).toList();
    }

    @Test
    @DisplayName("queueing twice does not put the same player in twice")
    void enqueueIsIdempotent() {
        String userId = user();

        matchmaking.enqueue(userId, "Player", null);
        matchmaking.enqueue(userId, "Player", null);

        assertThat(queueRepository.findByStatusOrderByQueuedAtAsc(QueueStatus.QUEUED))
                .filteredOn(entry -> entry.getUserId().equals(userId))
                .hasSize(1);
    }

    @Test
    @DisplayName("queueing announces player.queued")
    void enqueuePublishesEvent() {
        matchmaking.enqueue(user(), "Player", null);
        assertThat(eventTypes()).contains(EventType.PLAYER_QUEUED);
    }

    @Test
    @DisplayName("leaving the queue cancels the entry")
    void cancelRemovesFromQueue() {
        String userId = user();
        matchmaking.enqueue(userId, "Player", null);

        matchmaking.cancel(userId);

        assertThat(queueRepository.findFirstByUserIdAndStatus(userId, QueueStatus.QUEUED)).isEmpty();
    }

    @Test
    @DisplayName("two waiting players are paired and the match is announced")
    void pairsTwoWaitingPlayers() {
        String first = user();
        String second = user();
        matchmaking.enqueue(first, "First", "BLITZ");
        matchmaking.enqueue(second, "Second", "BLITZ");

        assertThat(matchmaking.pairOnce()).isEqualTo(1);

        assertThat(eventTypes()).contains(EventType.MATCH_FOUND);
        assertThat(matchRepository.count()).isGreaterThanOrEqualTo(1);

        MatchmakingStatusDto status = matchmaking.status(first);
        assertThat(status.matched()).isTrue();
        assertThat(status.gameId()).isNotBlank();
    }

    @Test
    @DisplayName("the paired players' entry is no longer QUEUED")
    void pairingConsumesTheEntries() {
        String first = user();
        String second = user();
        matchmaking.enqueue(first, "First", "BLITZ");
        matchmaking.enqueue(second, "Second", "BLITZ");

        matchmaking.pairOnce();

        assertThat(queueRepository.findFirstByUserIdAndStatus(first, QueueStatus.QUEUED)).isEmpty();
        assertThat(queueRepository.findFirstByUserIdAndStatus(second, QueueStatus.QUEUED)).isEmpty();
    }

    @Test
    @DisplayName("a single waiting player is not paired")
    void doesNotPairOnePlayer() {
        matchmaking.enqueue(user(), "Lonely", "BLITZ");
        assertThat(matchmaking.pairOnce()).isZero();
    }

    @Test
    @DisplayName("players on different time controls are not paired")
    void doesNotPairDifferentTimeControls() {
        String first = user();
        String second = user();
        matchmaking.enqueue(first, "First", "BLITZ");
        matchmaking.enqueue(second, "Second", "RAPID");

        assertThat(matchmaking.pairOnce()).isZero();
        assertThat(matchmaking.status(first).queued()).isTrue();
    }

    @Test
    @DisplayName("a player is never paired with themselves, even if the queue somehow holds two entries")
    void neverPairsAPlayerWithThemselves() {
        String userId = user();
        // Bypass enqueue's idempotency to create the pathological state directly.
        queueRepository.save(new QueueEntry(UUID.randomUUID().toString(), userId, "Solo", "BLITZ"));
        queueRepository.save(new QueueEntry(UUID.randomUUID().toString(), userId, "Solo", "BLITZ"));

        assertThat(matchmaking.pairOnce()).isZero();
    }

    @Test
    @DisplayName("status is empty for a player who never queued")
    void statusForIdlePlayer() {
        MatchmakingStatusDto status = matchmaking.status(user());
        assertThat(status.queued()).isFalse();
        assertThat(status.matched()).isFalse();
        assertThat(status.gameId()).isNull();
    }

    @Test
    @DisplayName("an anonymous caller cannot queue")
    void anonymousCannotQueue() {
        assertThatThrownBy(() -> matchmaking.enqueue(null, "Nobody", null))
                .isInstanceOf(AccessDeniedException.class);
    }
}
