package com.ccmmd.chess.gateway.dto;

/**
 * The response to a reset request. {@code token} is null in every real deployment; auth-service
 * populates it only when its development-only token exposure is enabled.
 */
public record PasswordResetResponse(String token) {
}
