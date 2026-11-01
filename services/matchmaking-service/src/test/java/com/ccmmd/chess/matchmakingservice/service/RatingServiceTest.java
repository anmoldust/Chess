package com.ccmmd.chess.matchmakingservice.service;

import com.ccmmd.chess.matchmakingservice.domain.Rating;
import com.ccmmd.chess.matchmakingservice.dto.RatingChangeDto;
import com.ccmmd.chess.matchmakingservice.dto.RatingDto;
import com.ccmmd.chess.matchmakingservice.repository.RatingRepository;
import com.ccmmd.chess.platform.events.payload.GameEnded;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Rolled back after each test, like the matchmaking suite: ratings are shared, mutable state. */
@SpringBootTest
@org.springframework.transaction.annotation.Transactional
class RatingServiceTest {

    @Autowired
    private RatingService ratings;

    @Autowired
    private RatingRepository repository;

    private static String user() {
        return UUID.randomUUID().toString();
    }

    private static GameEnded ended(String status, String whiteId, String blackId) {
        return new GameEnded(UUID.randomUUID().toString(), status, null, 20, whiteId, blackId);
    }

    @Test
    @DisplayName("a win moves the winner up, the loser down, and both onto one game played")
    void aDecisiveGameMovesBothRatings() {
        String white = user();
        String black = user();

        ratings.applyResult(ended("WHITE_WON", white, black));

        RatingDto winner = ratings.get(white);
        RatingDto loser = ratings.get(black);
        assertThat(winner.rating()).isGreaterThan(Elo.DEFAULT_RATING);
        assertThat(loser.rating()).isLessThan(Elo.DEFAULT_RATING);
        assertThat(winner.gamesPlayed()).isEqualTo(1);
        assertThat(winner.wins()).isEqualTo(1);
        assertThat(loser.losses()).isEqualTo(1);
    }

    @Test
    @DisplayName("a draw leaves both ratings untouched but records the game")
    void aDrawIsNeutral() {
        String white = user();
        String black = user();

        ratings.applyResult(ended("DRAW", white, black));

        assertThat(ratings.get(white).rating()).isEqualTo(Elo.DEFAULT_RATING);
        assertThat(ratings.get(black).rating()).isEqualTo(Elo.DEFAULT_RATING);
        assertThat(ratings.get(white).draws()).isEqualTo(1);
    }

    @Test
    @DisplayName("a game against the computer is not rated")
    void gamesAgainstTheComputerAreNotRated() {
        String human = user();

        ratings.applyResult(ended("WHITE_WON", human, null));

        assertThat(repository.findById(human)).isEmpty();
    }

    @Test
    @DisplayName("an unknown player reads as the default rating without being written")
    void unknownPlayerReadsAsDefault() {
        String stranger = user();

        RatingDto reading = ratings.get(stranger);

        assertThat(reading.rating()).isEqualTo(Elo.DEFAULT_RATING);
        assertThat(reading.gamesPlayed()).isZero();
        assertThat(repository.findById(stranger)).isEmpty();
    }

    @Test
    @DisplayName("each finish is recorded as a rating change for both players")
    void recordsRatingChanges() {
        String white = user();
        String black = user();
        String gameId = UUID.randomUUID().toString();

        ratings.applyResult(new GameEnded(gameId, "WHITE_WON", "1-0", 20, white, black));

        List<RatingChangeDto> winnerHistory = ratings.history(white, 10);
        assertThat(winnerHistory).hasSize(1);
        assertThat(winnerHistory.get(0).gameId()).isEqualTo(gameId);
        assertThat(winnerHistory.get(0).delta()).isPositive();
        assertThat(ratings.history(black, 10)).hasSize(1);
    }

    @Test
    @DisplayName("a redelivered result does not move the ratings twice")
    void ratingIsIdempotentPerGame() {
        String white = user();
        String black = user();
        GameEnded ended = new GameEnded(UUID.randomUUID().toString(), "WHITE_WON", "1-0", 20, white, black);

        ratings.applyResult(ended);
        int afterFirst = ratings.get(white).rating();
        ratings.applyResult(ended);

        assertThat(ratings.get(white).rating()).isEqualTo(afterFirst);
        assertThat(ratings.history(white, 10)).hasSize(1);
    }
}
