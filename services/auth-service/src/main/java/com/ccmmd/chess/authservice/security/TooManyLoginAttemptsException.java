package com.ccmmd.chess.authservice.security;

public class TooManyLoginAttemptsException extends RuntimeException {

    public TooManyLoginAttemptsException(String message) {
        super(message);
    }
}
