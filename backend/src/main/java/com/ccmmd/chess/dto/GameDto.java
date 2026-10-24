package com.ccmmd.chess.dto;

import com.ccmmd.chess.domain.GameMode;
import com.ccmmd.chess.domain.GameStatus;
import com.ccmmd.chess.domain.PlayerColor;

import java.util.List;

public record GameDto(
        String id,
        String fen,
        GameStatus status,
        GameMode mode,
        String whitePlayer,
        String blackPlayer,
        String whiteUserId,
        String blackUserId,
        PlayerColor aiColor,
        PlayerColor turn,
        String result,
        List<MoveDto> moves,
        long version
) {
}
