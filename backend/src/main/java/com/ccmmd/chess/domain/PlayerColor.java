package com.ccmmd.chess.domain;

import java.util.concurrent.ThreadLocalRandom;

public enum PlayerColor {
    WHITE,
    BLACK,
    RANDOM;

    /** Collapse RANDOM into a concrete color. */
    public PlayerColor resolve() {
        if (this != RANDOM) {
            return this;
        }
        return ThreadLocalRandom.current().nextBoolean() ? WHITE : BLACK;
    }

    public PlayerColor opposite() {
        return this == WHITE ? BLACK : WHITE;
    }
}
