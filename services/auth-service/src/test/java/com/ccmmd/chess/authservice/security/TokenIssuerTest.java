package com.ccmmd.chess.authservice.security;

import com.ccmmd.chess.authservice.dto.UserDto;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TokenIssuerTest {

    private static final UserDto USER = new UserDto("user-123", "player@example.com", "Player One");

    @Autowired
    private TokenIssuer issuer;

    @Autowired
    private RsaKeyProvider keys;

    @Test
    @DisplayName("an issued token verifies against the published public key")
    void tokenVerifiesWithThePublicKey() throws Exception {
        String token = issuer.issue(USER).token();

        SignedJWT parsed = SignedJWT.parse(token);

        assertThat(parsed.verify(new RSASSAVerifier(keys.rsaKey().toPublicJWK()))).isTrue();
    }

    @Test
    @DisplayName("the token carries the identity downstream services rely on")
    void tokenCarriesIdentity() throws Exception {
        String token = issuer.issue(USER).token();

        var claims = SignedJWT.parse(token).getJWTClaimsSet();

        assertThat(claims.getSubject()).isEqualTo("user-123");
        assertThat(claims.getIssuer()).isEqualTo("ccmmd-auth");
        assertThat(claims.getStringClaim("email")).isEqualTo("player@example.com");
        assertThat(claims.getStringClaim("name")).isEqualTo("Player One");
        assertThat(claims.getJWTID()).isNotBlank();
    }

    @Test
    @DisplayName("the token expires at the configured TTL")
    void tokenExpires() throws Exception {
        TokenIssuer.IssuedToken issued = issuer.issue(USER);

        var claims = SignedJWT.parse(issued.token()).getJWTClaimsSet();

        // A JWT `exp` is a NumericDate, i.e. whole seconds — our Instant carries nanoseconds, so
        // compare at the precision the token can actually represent.
        assertThat(claims.getExpirationTime().toInstant())
                .isEqualTo(issued.expiresAt().truncatedTo(ChronoUnit.SECONDS));
        assertThat(issued.expiresAt()).isBetween(
                Instant.now().plus(issuer.ttl()).minusSeconds(5),
                Instant.now().plus(issuer.ttl()).plusSeconds(5));
    }

    @Test
    @DisplayName("the JWKS exposes the public key and never the private one")
    void jwksExposesOnlyPublicMaterial() {
        Map<String, Object> jwks = new JWKSet(keys.rsaKey().toPublicJWK()).toJSONObject();

        @SuppressWarnings("unchecked")
        var published = (java.util.List<Map<String, Object>>) jwks.get("keys");
        assertThat(published).hasSize(1);

        Map<String, Object> key = published.get(0);
        assertThat(key).containsKeys("n", "e", "kid", "kty");
        // "d" is the RSA private exponent — leaking it would let anyone mint tokens.
        assertThat(key).doesNotContainKey("d");
        assertThat(key).doesNotContainKeys("p", "q");
        assertThat(key.get("kty")).isEqualTo("RSA");
    }
}
