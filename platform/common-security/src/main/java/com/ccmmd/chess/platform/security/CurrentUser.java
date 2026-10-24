package com.ccmmd.chess.platform.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

/**
 * Reads the caller's identity out of the verified JWT. Services must use this rather than any
 * {@code X-User-*} header: a header is only trustworthy because the gateway set it, whereas a
 * JWT is cryptographically verified on every request.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<Jwt> jwt(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(jwt);
        }
        return Optional.empty();
    }

    /** The account id ({@code sub}), or null when the caller is not authenticated. */
    public static String id(Authentication authentication) {
        return jwt(authentication).map(Jwt::getSubject).orElse(null);
    }

    public static String email(Authentication authentication) {
        return jwt(authentication).map(jwt -> jwt.getClaimAsString("email")).orElse(null);
    }

    public static String displayName(Authentication authentication) {
        return jwt(authentication).map(jwt -> jwt.getClaimAsString("name")).orElse(null);
    }
}
