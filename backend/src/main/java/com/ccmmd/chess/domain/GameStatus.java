package com.ccmmd.chess.domain;

public enum GameStatus {
    WAITING_FOR_OPPONENT,
    IN_PROGRESS,
    WHITE_WON,
    BLACK_WON,
    DRAW;

    public boolean isOver() {
        return this == WHITE_WON || this == BLACK_WON || this == DRAW;
    }
}
