package com.ccmmd.chess.gameservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.security.Principal;

/**
 * Reads the caller out of the verified JWT, for both HTTP (an {@link Authentication}) and STOMP
 * (a {@link Principal} carried on the handshake).
 *
 * <p>The token is the only trustworthy source of identity here. A header could be forged by
 * anything able to reach this service directly; the signature cannot.
 */
public final class JwtPrincipals {

    private JwtPrincipals() {
    }

    public static Jwt jwt(Principal principal) {
        if (principal instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }

    /** The account id ({@code sub}), or null when the caller is anonymous. */
    public static String userId(Principal principal) {
        if (principal == null) {
            return null;
        }
        Jwt jwt = jwt(principal);
        return jwt != null ? jwt.getSubject() : principal.getName();
    }

    public static String displayName(Principal principal) {
        Jwt jwt = jwt(principal);
        return jwt == null ? null : jwt.getClaimAsString("name");
    }
}
