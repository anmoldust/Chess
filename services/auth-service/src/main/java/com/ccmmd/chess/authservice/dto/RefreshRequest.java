package com.ccmmd.chess.authservice.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Refresh is authorised by an internal shared secret, not by the caller's own token: the gateway
 * is minting a token on behalf of a user it already authenticated, so it cannot present that
 * user's token (which is exactly what it is trying to obtain).
 */
public record RefreshRequest(@NotBlank String userId) {
}
