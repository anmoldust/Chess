package com.ccmmd.chess.gameservice.dto;

import com.ccmmd.chess.gameservice.domain.GameMode;
import com.ccmmd.chess.gameservice.domain.PlayerColor;

/**
 * The creator's identity comes from the verified JWT, not from the body. {@code difficulty} is
 * only used for AI games and defaults when absent.
 */
public record CreateGameRequest(
        GameMode mode,
        PlayerColor preferredColor,
        String difficulty
) {
}
