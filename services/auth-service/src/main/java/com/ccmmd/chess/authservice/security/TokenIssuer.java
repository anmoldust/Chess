package com.ccmmd.chess.authservice.security;

import com.ccmmd.chess.authservice.dto.UserDto;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** Mints the short-lived RS256 access token every downstream service verifies. */
@Component
public class TokenIssuer {

    public record IssuedToken(String token, Instant expiresAt) {
    }

    private final RsaKeyProvider keyProvider;
    private final String issuer;
    private final Duration ttl;

    public TokenIssuer(
            RsaKeyProvider keyProvider,
            @Value("${ccmmd.auth.jwt.issuer:ccmmd-auth}") String issuer,
            @Value("${ccmmd.auth.jwt.ttl:PT15M}") Duration ttl) {
        this.keyProvider = keyProvider;
        this.issuer = issuer;
        this.ttl = ttl;
    }

    public Duration ttl() {
        return ttl;
    }

    public IssuedToken issue(UserDto user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ttl);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(user.id())
                .claim("email", user.email())
                .claim("name", user.displayName())
                .issueTime(Date.from(issuedAt))
                .notBeforeTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyProvider.rsaKey().getKeyID()).build(),
                claims);

        try {
            jwt.sign(new RSASSASigner(keyProvider.rsaKey()));
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot sign the access token", failure);
        }

        return new IssuedToken(jwt.serialize(), expiresAt);
    }
}
