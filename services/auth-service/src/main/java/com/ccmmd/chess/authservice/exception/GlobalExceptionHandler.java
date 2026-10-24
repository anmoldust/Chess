package com.ccmmd.chess.authservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyUsedException.class)
    ProblemDetail handleEmailUsed(EmailAlreadyUsedException ex) {
        return problem(HttpStatus.CONFLICT, "Email already registered", ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        // Deliberately not distinguished from "no such account": telling a caller which half was
        // wrong turns the login endpoint into an account-enumeration oracle.
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password");
    }

    @ExceptionHandler(com.ccmmd.chess.authservice.security.TooManyLoginAttemptsException.class)
    ProblemDetail handleTooManyAttempts(com.ccmmd.chess.authservice.security.TooManyLoginAttemptsException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", detail);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
