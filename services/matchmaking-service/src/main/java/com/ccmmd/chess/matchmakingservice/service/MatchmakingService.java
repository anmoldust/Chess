package com.ccmmd.chess.matchmakingservice.service;

import com.ccmmd.chess.matchmakingservice.domain.MatchRecord;
import com.ccmmd.chess.matchmakingservice.domain.QueueEntry;
import com.ccmmd.chess.matchmakingservice.domain.QueueStatus;
import com.ccmmd.chess.matchmakingservice.dto.MatchmakingStatusDto;
import com.ccmmd.chess.matchmakingservice.dto.QueueStatusDto;
import com.ccmmd.chess.matchmakingservice.repository.MatchRepository;
import com.ccmmd.chess.matchmakingservice.repository.QueueRepository;
import com.ccmmd.chess.platform.events.EventType;
import com.ccmmd.chess.platform.events.payload.MatchFound;
import com.ccmmd.chess.platform.events.payload.PlayerQueued;
import com.ccmmd.chess.platform.messaging.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class MatchmakingService {

    private static final Logger log = LoggerFactory.getLogger(MatchmakingService.class);
    private static final String PRODUCER = "matchmaking-service";
    private static final String DEFAULT_TIME_CONTROL = "BLITZ";

    private final QueueRepository queueRepository;
    private final MatchRepository matchRepository;
    private final OutboxPublisher outbox;
    private final int maxPairsPerTick;

    public MatchmakingService(
            QueueRepository queueRepository,
            MatchRepository matchRepository,
            OutboxPublisher outbox,
            @Value("${ccmmd.matchmaking.max-pairs-per-tick:25}") int maxPairsPerTick) {
        this.queueRepository = queueRepository;
        this.matchRepository = matchRepository;
        this.outbox = outbox;
        this.maxPairsPerTick = maxPairsPerTick;
    }

    @Transactional
    public QueueStatusDto enqueue(String userId, String displayName, String timeControl) {
        requireSignedIn(userId);
        String control = normalizeTimeControl(timeControl);

        // Idempotent: a second tap must not put the same player in the queue twice.
        Optional<QueueEntry> alreadyQueued =
                queueRepository.findFirstByUserIdAndStatus(userId, QueueStatus.QUEUED);
        if (alreadyQueued.isPresent()) {
            return toDto(alreadyQueued.get());
        }

        QueueEntry entry = new QueueEntry(
                UUID.randomUUID().toString(), userId, normalizeName(displayName), control);
        queueRepository.saveAndFlush(entry);

        outbox.publish(
                EventType.PLAYER_QUEUED,
                PRODUCER,
                entry.getId(),
                null,
                new PlayerQueued(userId, entry.getDisplayName(), control, entry.getQueuedAt()));

        return toDto(entry);
    }

    @Transactional
    public void cancel(String userId) {
        requireSignedIn(userId);
        queueRepository.findFirstByUserIdAndStatus(userId, QueueStatus.QUEUED)
                .ifPresent(entry -> {
                    entry.cancel();
                    queueRepository.saveAndFlush(entry);
                });
    }

    @Transactional(readOnly = true)
    public MatchmakingStatusDto status(String userId) {
        requireSignedIn(userId);

        Optional<QueueEntry> latest = queueRepository.findFirstByUserIdOrderByQueuedAtDesc(userId);
        if (latest.isEmpty()) {
            return new MatchmakingStatusDto(false, false, null);
        }

        QueueEntry entry = latest.get();
        if (entry.getStatus() == QueueStatus.QUEUED) {
            return new MatchmakingStatusDto(true, false, null);
        }
        if (entry.getStatus() == QueueStatus.MATCHED && entry.getMatchId() != null) {
            return matchRepository.findById(entry.getMatchId())
                    .map(match -> new MatchmakingStatusDto(false, true, match.getGameId()))
                    .orElseGet(() -> new MatchmakingStatusDto(false, false, null));
        }
        return new MatchmakingStatusDto(false, false, null);
    }

    /**
     * Pairs the longest-waiting players with the same time control.
     *
     * <p>Each pair is re-read under a row lock before being claimed: with more than one instance,
     * the candidate list is only a hint, and the lock plus the status re-check is what stops two
     * instances pairing the same player. Pairing, the match row, and the announcement all commit
     * in one transaction via the outbox.
     */
    @Scheduled(
            fixedDelayString = "${ccmmd.matchmaking.interval-ms:1000}",
            initialDelayString = "${ccmmd.matchmaking.initial-delay-ms:2000}")
    @Transactional
    public void runMatcher() {
        for (int paired = 0; paired < maxPairsPerTick; paired++) {
            if (pairOnce() == 0) {
                return;
            }
        }
    }

    /** @return how many pairs were formed (0 or 1) */
    int pairOnce() {
        List<QueueEntry> waiting = queueRepository.findByStatusOrderByQueuedAtAsc(QueueStatus.QUEUED);
        if (waiting.size() < 2) {
            return 0;
        }

        QueueEntry first = waiting.get(0);
        QueueEntry second = waiting.stream()
                .skip(1)
                .filter(candidate -> candidate.getTimeControl().equals(first.getTimeControl()))
                .findFirst()
                .orElse(null);
        if (second == null) {
            return 0;
        }

        QueueEntry left = queueRepository.findByIdForUpdate(first.getId()).orElse(null);
        QueueEntry right = queueRepository.findByIdForUpdate(second.getId()).orElse(null);
        if (left == null || right == null
                || left.getStatus() != QueueStatus.QUEUED || right.getStatus() != QueueStatus.QUEUED) {
            return 0; // another instance claimed one of them first
        }
        if (left.getUserId().equals(right.getUserId())) {
            return 0; // never pair a player with themselves
        }

        String matchId = UUID.randomUUID().toString();
        String gameId = UUID.randomUUID().toString();

        boolean leftIsWhite = ThreadLocalRandom.current().nextBoolean();
        QueueEntry white = leftIsWhite ? left : right;
        QueueEntry black = leftIsWhite ? right : left;

        left.markMatched(matchId);
        right.markMatched(matchId);
        queueRepository.saveAll(List.of(left, right));

        MatchRecord match = matchRepository.saveAndFlush(new MatchRecord(
                matchId, gameId, white.getUserId(), black.getUserId(), left.getTimeControl()));

        outbox.publish(
                EventType.MATCH_FOUND,
                PRODUCER,
                matchId,
                null,
                new MatchFound(
                        matchId,
                        gameId,
                        white.getUserId(),
                        black.getUserId(),
                        white.getDisplayName(),
                        black.getDisplayName(),
                        left.getTimeControl(),
                        match.getMatchedAt()));

        log.info("matched {} vs {} as game {}", white.getUserId(), black.getUserId(), gameId);
        return 1;
    }

    private QueueStatusDto toDto(QueueEntry entry) {
        List<QueueEntry> waiting = queueRepository.findByStatusOrderByQueuedAtAsc(QueueStatus.QUEUED);
        int position = 0;
        for (int i = 0; i < waiting.size(); i++) {
            if (waiting.get(i).getId().equals(entry.getId())) {
                position = i + 1;
                break;
            }
        }
        return new QueueStatusDto(true, position, entry.getQueuedAt());
    }

    private static void requireSignedIn(String userId) {
        if (userId == null) {
            throw new AccessDeniedException("Sign in to use matchmaking");
        }
    }

    private static String normalizeTimeControl(String timeControl) {
        if (timeControl == null || timeControl.isBlank()) {
            return DEFAULT_TIME_CONTROL;
        }
        String trimmed = timeControl.trim().toUpperCase(Locale.ROOT);
        return trimmed.length() > 24 ? DEFAULT_TIME_CONTROL : trimmed;
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return "Player";
        }
        String trimmed = name.trim();
        return trimmed.length() > 60 ? trimmed.substring(0, 60) : trimmed;
    }
}
