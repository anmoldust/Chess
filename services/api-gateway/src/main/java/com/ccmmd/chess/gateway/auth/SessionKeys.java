package com.ccmmd.chess.gateway.auth;

/** Session attribute keys. The access token lives here, never in anything the browser can read. */
public final class SessionKeys {

    public static final String ACCESS_TOKEN = "ccmmd.accessToken";
    public static final String ACCESS_TOKEN_EXPIRES_AT = "ccmmd.accessTokenExpiresAt";

    private SessionKeys() {
    }
}
