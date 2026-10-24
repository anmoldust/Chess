package com.ccmmd.chess.gameservice.domain;

import java.util.Locale;

/**
 * A time control is an initial budget plus an increment added after each move.
 *
 * <p>Previously this existed only as a label on a matchmaking entry that game-service never read,
 * so "Blitz" meant nothing. It now drives an actual clock.
 */
public enum TimeControl {

    BULLET(60_000L, 0L),
    BLITZ(300_000L, 2_000L),
    RAPID(900_000L, 10_000L),
    CLASSICAL(1_800_000L, 30_000L);

    private final long initialMs;
    private final long incrementMs;

    TimeControl(long initialMs, long incrementMs) {
        this.initialMs = initialMs;
        this.incrementMs = incrementMs;
    }

    public long initialMs() {
        return initialMs;
    }

    public long incrementMs() {
        return incrementMs;
    }

    /** Unknown or absent values fall back to BLITZ rather than failing a game. */
    public static TimeControl from(String value) {
        if (value == null || value.isBlank()) {
            return BLITZ;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return BLITZ;
        }
    }
}
