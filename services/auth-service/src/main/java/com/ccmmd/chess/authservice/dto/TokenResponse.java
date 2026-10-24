package com.ccmmd.chess.authservice.dto;

import java.time.Instant;

/**
 * What the gateway stores in its session. The token is short-lived; the gateway refreshes it
 * before it expires rather than the browser ever seeing it.
 */
public record TokenResponse(UserDto user, String token, Instant expiresAt) {
}
