package com.ccmmd.chess.authservice.web;

import com.ccmmd.chess.authservice.security.RsaKeyProvider;
import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The public half of the signing key, in JWKS form. Every other service fetches this to verify
 * tokens; it is public by design and contains no secret material.
 */
@RestController
public class JwksController {

    private final RsaKeyProvider keyProvider;

    public JwksController(RsaKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return new JWKSet(keyProvider.rsaKey().toPublicJWK()).toJSONObject();
    }
}
