package com.ccmmd.chess.engineservice.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EngineDifficultyTest {

    @Test
    @DisplayName("each level weakens both search and skill together")
    void levelsWeakenTogether() {
        assertThat(EngineDifficulty.EASY.depth()).isLessThan(EngineDifficulty.MEDIUM.depth());
        assertThat(EngineDifficulty.MEDIUM.depth()).isLessThan(EngineDifficulty.HARD.depth());
        assertThat(EngineDifficulty.EASY.skill()).isLessThan(EngineDifficulty.HARD.skill());
    }

    @Test
    @DisplayName("parsing is case-insensitive")
    void parsesCaseInsensitively() {
        assertThat(EngineDifficulty.from("hard")).isEqualTo(EngineDifficulty.HARD);
        assertThat(EngineDifficulty.from(" Easy ")).isEqualTo(EngineDifficulty.EASY);
    }

    @Test
    @DisplayName("a missing or unknown level falls back to MEDIUM rather than failing a game")
    void fallsBackToMedium() {
        assertThat(EngineDifficulty.from(null)).isEqualTo(EngineDifficulty.MEDIUM);
        assertThat(EngineDifficulty.from("")).isEqualTo(EngineDifficulty.MEDIUM);
        assertThat(EngineDifficulty.from("impossible")).isEqualTo(EngineDifficulty.MEDIUM);
    }
}
