package com.ccmmd.chess.platform.events.payload;

/**
 * Emitted after a move is durably applied. This is a notification of a fact that already
 * happened — the move itself is never made through the bus.
 */
public record MovePlayed(
        String gameId,
        int ply,
        String uci,
        String fen,
        String status,
        String byUserId
) {
}
