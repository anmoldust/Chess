package com.ccmmd.chess.gameservice.exception;

public class GameConflictException extends RuntimeException {

    public GameConflictException(String message) {
        super(message);
    }
}
