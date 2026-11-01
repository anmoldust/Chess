package com.ccmmd.chess.authservice.exception;

/** An unknown, already-used or expired reset token. Deliberately one message for all three. */
public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException(String message) {
        super(message);
    }
}
