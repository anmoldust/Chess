package com.ccmmd.chess.gameservice.dto;

import com.ccmmd.chess.gameservice.domain.GameEndReason;
import com.ccmmd.chess.gameservice.domain.GameMode;
import com.ccmmd.chess.gameservice.domain.GameStatus;
import com.ccmmd.chess.gameservice.domain.PlayerColor;

import java.util.List;

/**
 * One payload for every viewer. The seat owner ids let a client work out which color it is,
 * so the server never has to send a per-viewer variant — which is what makes broadcasting
 * one message to a topic possible.
 *
 * <p>Clocks are sent as remaining milliseconds at the moment of this snapshot; the client ticks
 * its own copy for the side to move and resyncs on the next broadcast.
 */
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
        GameEndReason endReason,
        Long whiteTimeMs,
        Long blackTimeMs,
        long incrementMs,
        List<MoveDto> moves,
        long version
) {
}
