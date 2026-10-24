package com.ccmmd.chess.security;

import org.springframework.security.core.Authentication;

import java.util.Optional;

/** Reads the authenticated account out of a request's Authentication, or empty when anonymous. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<UserPrincipal> principal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return Optional.of(userPrincipal);
        }
        return Optional.empty();
    }

    public static String id(Authentication authentication) {
        return principal(authentication).map(UserPrincipal::getId).orElse(null);
    }

    public static String displayName(Authentication authentication) {
        return principal(authentication).map(UserPrincipal::getDisplayName).orElse(null);
    }
}
