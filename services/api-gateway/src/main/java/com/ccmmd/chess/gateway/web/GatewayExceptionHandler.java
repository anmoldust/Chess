package com.ccmmd.chess.gateway.web;

import com.ccmmd.chess.gateway.auth.DownstreamProblem;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

@RestControllerAdvice
public class GatewayExceptionHandler {

    /** Preserve auth-service's status and reason rather than flattening it to a 500. */
    @ExceptionHandler(DownstreamProblem.class)
    public ResponseEntity<ProblemDetail> handleDownstream(DownstreamProblem problem) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(problem.status(), problem.detail());
        detail.setTitle(problem.title());
        return ResponseEntity.status(problem.status()).body(detail);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ProblemDetail> handleValidation(WebExchangeBindException failure) {
        String detail = failure.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid request");
        return ResponseEntity.badRequest().body(problem);
    }
}
