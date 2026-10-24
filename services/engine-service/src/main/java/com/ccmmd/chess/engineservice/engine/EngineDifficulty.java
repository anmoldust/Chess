package com.ccmmd.chess.engineservice.engine;

import java.util.Locale;

/**
 * How strong to play. Depth and UCI skill level move together: lowering only the skill level still
 * costs full search time, and lowering only the depth still plays sharp tactics.
 */
public enum EngineDifficulty {

    EASY(4, 3),
    MEDIUM(10, 12),
    HARD(15, 20);

    private final int depth;
    private final int skill;

    EngineDifficulty(int depth, int skill) {
        this.depth = depth;
        this.skill = skill;
    }

    public int depth() {
        return depth;
    }

    public int skill() {
        return skill;
    }

    /** Unknown or absent values fall back to MEDIUM rather than failing a game. */
    public static EngineDifficulty from(String value) {
        if (value == null || value.isBlank()) {
            return MEDIUM;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return MEDIUM;
        }
    }
}
