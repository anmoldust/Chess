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
        /** The named time control (BULLET/BLITZ/RAPID/CLASSICAL), or null for a clock without a name. */
        String timeControl,
        String whitePlayer,
        String blackPlayer,
        String whiteUserId,
        String blackUserId,
        PlayerColor aiColor,
        PlayerColor turn,
        String result,
        GameEndReason endReason,
        /** The side with an open draw offer, or null. The opponent accepts by offering back. */
        PlayerColor drawOfferBy,
        /** The side with an open rematch offer, or null. Both sides offering starts a new game. */
        PlayerColor rematchOfferBy,
        /** Set once a rematch is agreed: the id of the new game both clients should open. */
        String rematchGameId,
        Long whiteTimeMs,
        Long blackTimeMs,
        long incrementMs,
        List<MoveDto> moves,
        long version
) {
}
