package com.ccmmd.chess.dto;

import com.ccmmd.chess.domain.GameMode;
import com.ccmmd.chess.domain.PlayerColor;

public record CreateGameRequest(
        GameMode mode,
        String creatorName,
        PlayerColor preferredColor
) {
}
