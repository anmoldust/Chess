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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final RatingService ratingService;
    private final int maxPairsPerTick;
    private final int ratingWindowBase;
    private final int ratingWindowGrowthPerSecond;

    public MatchmakingService(
            QueueRepository queueRepository,
            MatchRepository matchRepository,
            OutboxPublisher outbox,
            RatingService ratingService,
            @Value("${ccmmd.matchmaking.max-pairs-per-tick:25}") int maxPairsPerTick,
            @Value("${ccmmd.matchmaking.rating-window-base:100}") int ratingWindowBase,
            @Value("${ccmmd.matchmaking.rating-window-growth-per-second:20}") int ratingWindowGrowthPerSecond) {
        this.queueRepository = queueRepository;
        this.matchRepository = matchRepository;
        this.outbox = outbox;
        this.ratingService = ratingService;
        this.maxPairsPerTick = maxPairsPerTick;
        this.ratingWindowBase = ratingWindowBase;
        this.ratingWindowGrowthPerSecond = ratingWindowGrowthPerSecond;
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

        // Keep a name for the leaderboard, but only for a player who already has a rating row.
        ratingService.rememberName(userId, entry.getDisplayName());

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
     * Pairs waiting players, preferring the closest rating within the same time control.
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

        Map<String, Integer> ratings = ratingService.ratingsByUser(
                waiting.stream().map(QueueEntry::getUserId).toList());
        Instant now = Instant.now();

        // Oldest first: whoever has waited longest gets first pick of the opponents their rating
        // allows. If they have none, the next-longest wait is tried, so one stranded player does
        // not hold up everyone queued behind them.
        for (int i = 0; i < waiting.size(); i++) {
            QueueEntry partner = bestPartner(waiting, i, ratings, now);
            if (partner != null && pair(waiting.get(i), partner)) {
                return 1;
            }
        }
        return 0;
    }

    /** The rating-closest opponent within the first player's current tolerance, or null if none. */
    private QueueEntry bestPartner(List<QueueEntry> waiting, int index, Map<String, Integer> ratings, Instant now) {
        QueueEntry first = waiting.get(index);
        long window = allowedRatingDelta(first.getQueuedAt(), now);
        int firstRating = ratingOf(ratings, first.getUserId());

        QueueEntry best = null;
        int bestDelta = Integer.MAX_VALUE;
        for (int j = 0; j < waiting.size(); j++) {
            if (j == index) {
                continue;
            }
            QueueEntry candidate = waiting.get(j);
            if (!candidate.getTimeControl().equals(first.getTimeControl())) {
                continue;
            }
            if (candidate.getUserId().equals(first.getUserId())) {
                continue; // never pair a player with themselves
            }
            int delta = Math.abs(ratingOf(ratings, candidate.getUserId()) - firstRating);
            if (delta > window) {
                continue;
            }
            // Strictly closer, so a tie keeps the earliest-queued candidate (we scan in queue order).
            if (delta < bestDelta) {
                bestDelta = delta;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * How far apart two ratings may be and still be paired.
     *
     * <p>Widens the longer a player has waited, so two far-apart players are not stranded forever
     * in a small pool: after a minute the window admits almost anyone. This is the fairness valve
     * between "a good game now" and "any game soon".
     */
    long allowedRatingDelta(Instant queuedAt, Instant now) {
        long waitedSeconds = Math.max(0L, Duration.between(queuedAt, now).getSeconds());
        return ratingWindowBase + waitedSeconds * ratingWindowGrowthPerSecond;
    }

    private boolean pair(QueueEntry first, QueueEntry second) {
        // Re-read under a row lock: the candidate list is only a hint, and the lock plus the status
        // re-check is what stops two instances claiming the same player.
        QueueEntry left = queueRepository.findByIdForUpdate(first.getId()).orElse(null);
        QueueEntry right = queueRepository.findByIdForUpdate(second.getId()).orElse(null);
        if (left == null || right == null
                || left.getStatus() != QueueStatus.QUEUED || right.getStatus() != QueueStatus.QUEUED) {
            return false; // another instance claimed one of them first
        }
        if (left.getUserId().equals(right.getUserId())) {
            return false;
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
        return true;
    }

    private static int ratingOf(Map<String, Integer> ratings, String userId) {
        return ratings.getOrDefault(userId, Elo.DEFAULT_RATING);
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
