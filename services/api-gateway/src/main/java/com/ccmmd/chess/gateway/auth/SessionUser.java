package com.ccmmd.chess.gateway.auth;

import java.io.Serializable;

/**
 * Who the browser is, as far as the gateway is concerned. This — not the request body, not a
 * header a client could set — is the identity that gets turned into a JWT for the services.
 *
 * <p>Must be {@link Serializable}: Spring Session stores the security context in Redis using JDK
 * serialisation, and a plain record is not serialisable. Without this, the first request that
 * saves a session fails at the serialisation step — which in-memory sessions would never reveal.
 */
public record SessionUser(String id, String email, String displayName) implements Serializable {

    private static final long serialVersionUID = 1L;
}
