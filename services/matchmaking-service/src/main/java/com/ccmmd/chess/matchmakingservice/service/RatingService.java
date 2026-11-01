package com.ccmmd.chess.matchmakingservice.service;

import com.ccmmd.chess.matchmakingservice.domain.Rating;
import com.ccmmd.chess.matchmakingservice.domain.RatingChange;
import com.ccmmd.chess.matchmakingservice.dto.RatingChangeDto;
import com.ccmmd.chess.matchmakingservice.dto.RatingDto;
import com.ccmmd.chess.matchmakingservice.repository.RatingChangeRepository;
import com.ccmmd.chess.matchmakingservice.repository.RatingRepository;
import com.ccmmd.chess.platform.events.payload.GameEnded;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RatingService {

    private final RatingRepository ratings;
    private final RatingChangeRepository changes;

    public RatingService(RatingRepository ratings, RatingChangeRepository changes) {
        this.ratings = ratings;
        this.changes = changes;
    }

    /**
     * Applies one finished game to both players' ratings.
     *
     * <p>Runs inside the consumer's transaction, so the two rating rows and the "already handled"
     * marker commit together — a redelivered event cannot move a rating twice.
     */
    @Transactional
    public void applyResult(GameEnded ended) {
        String whiteId = ended.whiteUserId();
        String blackId = ended.blackUserId();

        // A computer seat is null; a game with no second human has nothing to rate. Self-play is
        // skipped too, since it produces no information about relative strength.
        if (whiteId == null || blackId == null || whiteId.equals(blackId)) {
            return;
        }
        Double whiteScore = whiteScore(ended.status());
        if (whiteScore == null) {
            return;
        }
        // Rating a game twice would move both players' numbers twice; the unique key on
        // (game_id, user_id) is the backstop, this is the cheap guard.
        if (changes.existsByGameId(ended.gameId())) {
            return;
        }

        Rating white = loadOrCreate(whiteId);
        Rating black = loadOrCreate(blackId);

        int whiteBefore = white.getRating();
        int blackBefore = black.getRating();
        Elo.Outcome outcome = Elo.rate(
                whiteBefore, white.getGamesPlayed(),
                blackBefore, black.getGamesPlayed(), whiteScore);

        white.record(outcome.white(), whiteScore);
        black.record(outcome.black(), 1.0 - whiteScore);

        ratings.saveAll(List.of(white, black));
        changes.saveAll(List.of(
                new RatingChange(UUID.randomUUID().toString(), ended.gameId(), whiteId, whiteBefore, outcome.white()),
                new RatingChange(UUID.randomUUID().toString(), ended.gameId(), blackId, blackBefore, outcome.black())));
    }

    @Transactional(readOnly = true)
    public RatingDto get(String userId) {
        return ratings.findById(userId).map(RatingDto::of).orElseGet(() -> provisional(userId));
    }

    @Transactional(readOnly = true)
    public List<RatingDto> leaderboard(int limit) {
        int bounded = Math.max(1, Math.min(limit, 100));
        return ratings.findByGamesPlayedGreaterThanOrderByRatingDesc(0, PageRequest.of(0, bounded)).stream()
                .map(RatingDto::of)
                .toList();
    }

    /** A player's rating movements, newest first. */
    @Transactional(readOnly = true)
    public List<RatingChangeDto> history(String userId, int limit) {
        int bounded = Math.max(1, Math.min(limit, 100));
        return changes.findByUserIdOrderByPlayedAtDesc(userId, PageRequest.of(0, bounded)).stream()
                .map(RatingChangeDto::of)
                .toList();
    }

    /** Current ratings for a set of players, defaulted for anyone with no row yet. */
    @Transactional(readOnly = true)
    public Map<String, Integer> ratingsByUser(Collection<String> userIds) {
        Map<String, Integer> byUser = new HashMap<>();
        if (userIds.isEmpty()) {
            return byUser;
        }
        for (Rating rating : ratings.findAllById(userIds)) {
            byUser.put(rating.getUserId(), rating.getRating());
        }
        return byUser;
    }

    /**
     * Records a display name for the leaderboard. The row is created if this is a player's first
     * appearance (at the default rating, with no games), so a name is captured the moment they queue.
     */
    @Transactional
    public void rememberName(String userId, String displayName) {
        if (userId == null) {
            return;
        }
        Rating rating = loadOrCreate(userId);
        rating.rememberName(displayName);
        ratings.save(rating);
    }

    private Rating loadOrCreate(String userId) {
        return ratings.findById(userId).orElseGet(() -> new Rating(userId, Elo.DEFAULT_RATING));
    }

    private static Double whiteScore(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "WHITE_WON" -> 1.0;
            case "BLACK_WON" -> 0.0;
            case "DRAW" -> 0.5;
            default -> null;
        };
    }

    private static RatingDto provisional(String userId) {
        return new RatingDto(userId, null, Elo.DEFAULT_RATING, 0, 0, 0, 0);
    }
}
