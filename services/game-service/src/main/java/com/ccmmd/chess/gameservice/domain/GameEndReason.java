package com.ccmmd.chess.gameservice.domain;

/**
 * Why a game ended. The status says who won; this says how — which is what a player actually
 * wants to read, and what analysis and notifications need in order to say something useful.
 */
public enum GameEndReason {
    CHECKMATE,
    RESIGNATION,
    TIMEOUT,
    STALEMATE,
    INSUFFICIENT_MATERIAL,
    FIFTY_MOVE_RULE,
    THREEFOLD_REPETITION,
    /** Both players agreed to split the point. */
    DRAW_AGREED
}
