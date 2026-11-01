package com.ccmmd.chess.matchmakingservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EloTest {

    private static final int ESTABLISHED = 50;
    private static final int PROVISIONAL = 0;

    @Test
    @DisplayName("evenly matched players are expected to score half a point each")
    void expectedScoreIsHalfBetweenEquals() {
        assertThat(Elo.expectedScore(1200, 1200)).isEqualTo(0.5);
    }

    @Test
    @DisplayName("expectations are symmetric: the two players' expected scores sum to one")
    void expectationsAreSymmetric() {
        assertThat(Elo.expectedScore(1200, 1600) + Elo.expectedScore(1600, 1200)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("a decisive game between equals moves the winner up and the loser down by half the K")
    void decisiveGameBetweenEquals() {
        Elo.Outcome outcome = Elo.rate(1200, ESTABLISHED, 1200, ESTABLISHED, 1.0);

        int k = Elo.kFactor(ESTABLISHED);
        assertThat(outcome.white()).isEqualTo(1200 + k / 2);
        assertThat(outcome.black()).isEqualTo(1200 - k / 2);
    }

    @Test
    @DisplayName("a draw between equals changes nothing")
    void drawBetweenEqualsIsNeutral() {
        Elo.Outcome outcome = Elo.rate(1200, ESTABLISHED, 1200, ESTABLISHED, 0.5);

        assertThat(outcome.white()).isEqualTo(1200);
        assertThat(outcome.black()).isEqualTo(1200);
    }

    @Test
    @DisplayName("the underdog gains more for a win than the favourite would")
    void underdogGainsMoreThanFavourite() {
        Elo.Outcome upset = Elo.rate(1000, ESTABLISHED, 1600, ESTABLISHED, 1.0);
        Elo.Outcome expected = Elo.rate(1600, ESTABLISHED, 1000, ESTABLISHED, 1.0);

        int underdogGain = upset.white() - 1000;
        int favouriteGain = expected.white() - 1600;

        assertThat(underdogGain).isGreaterThan(favouriteGain);
        assertThat(favouriteGain).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("a provisional player's rating moves faster than an established one's")
    void provisionalPlayersMoveFaster() {
        Elo.Outcome provisional = Elo.rate(1200, PROVISIONAL, 1200, PROVISIONAL, 1.0);
        Elo.Outcome established = Elo.rate(1200, ESTABLISHED, 1200, ESTABLISHED, 1.0);

        assertThat(provisional.white() - 1200).isGreaterThan(established.white() - 1200);
    }

    @Test
    @DisplayName("a rating never falls below the floor")
    void ratingNeverDropsBelowTheFloor() {
        Elo.Outcome outcome = Elo.rate(Elo.FLOOR, PROVISIONAL, Elo.FLOOR, PROVISIONAL, 0.0);

        assertThat(outcome.white()).isEqualTo(Elo.FLOOR);
    }
}
