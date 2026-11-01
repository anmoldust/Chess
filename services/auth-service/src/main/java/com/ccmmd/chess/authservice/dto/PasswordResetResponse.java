package com.ccmmd.chess.authservice.dto;

/**
 * The response to a reset request. {@code token} is null in every real deployment — it is populated
 * only when {@code ccmmd.auth.reset.expose-token} is on, so the flow is usable without an email
 * provider during local development.
 */
public record PasswordResetResponse(String token) {
}
