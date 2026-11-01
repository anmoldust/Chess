package com.ccmmd.chess.matchmakingservice.service;

/**
 * Standard Elo, kept as pure arithmetic so it can be verified without a database.
 *
 * <p>A provisional player's rating moves faster than an established one's: with few games behind
 * the number, a single result carries more signal, so early ratings converge quickly and then
 * settle. This is the only place arithmetic decides a rating — {@link RatingService} just persists
 * the outcome.
 */
public final class Elo {

    /** Where every account starts. */
    public static final int DEFAULT_RATING = 1200;

    /** A rating never drops below this, so a run of losses cannot make an account unplayable. */
    public static final int FLOOR = 100;

    private static final int PROVISIONAL_GAMES = 20;
    private static final int PROVISIONAL_K = 40;
    private static final int ESTABLISHED_K = 20;

    private Elo() {
    }

    /** The score the first player is expected to get, from 0 (always loses) to 1 (always wins). */
    public static double expectedScore(int rating, int opponentRating) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponentRating - rating) / 400.0));
    }

    public static int kFactor(int gamesPlayed) {
        return gamesPlayed < PROVISIONAL_GAMES ? PROVISIONAL_K : ESTABLISHED_K;
    }

    /**
     * Both players' new ratings after one game.
     *
     * @param whiteScore white's result: 1 win, 0.5 draw, 0 loss
     */
    public static Outcome rate(
            int whiteRating, int whiteGames, int blackRating, int blackGames, double whiteScore) {
        double whiteExpected = expectedScore(whiteRating, blackRating);
        double blackExpected = expectedScore(blackRating, whiteRating);

        int whiteChange = (int) Math.round(kFactor(whiteGames) * (whiteScore - whiteExpected));
        int blackChange = (int) Math.round(kFactor(blackGames) * ((1.0 - whiteScore) - blackExpected));

        return new Outcome(clamp(whiteRating + whiteChange), clamp(blackRating + blackChange));
    }

    private static int clamp(int rating) {
        return Math.max(FLOOR, rating);
    }

    public record Outcome(int white, int black) {
    }
}
