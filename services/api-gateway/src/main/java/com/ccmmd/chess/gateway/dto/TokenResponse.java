package com.ccmmd.chess.gateway.dto;

import java.time.Instant;

/** Mirrors auth-service's response. The gateway keeps this in the session, never in the page. */
public record TokenResponse(UserDto user, String token, Instant expiresAt) {
}
