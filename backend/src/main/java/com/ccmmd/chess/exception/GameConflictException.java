package com.ccmmd.chess.exception;

public class GameConflictException extends RuntimeException {

    public GameConflictException(String message) {
        super(message);
    }
}
