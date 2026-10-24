package com.ccmmd.chess.authservice.security;

import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * The platform's single signing key. auth-service is the only component that holds the private
 * half; every other service gets the public half from the JWKS endpoint.
 *
 * <p>For local development the key is generated on first boot and written to disk so it survives
 * restarts (otherwise every restart would invalidate every previously issued token and every
 * cached JWKS). In production the path points at a mounted secret.
 */
@Component
public class RsaKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyProvider.class);
    private static final String BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String END = "-----END PRIVATE KEY-----";

    private final RSAKey rsaKey;

    public RsaKeyProvider(@Value("${ccmmd.auth.jwt.key-path}") String keyPath) {
        this.rsaKey = loadOrCreate(Path.of(keyPath));
    }

    public RSAKey rsaKey() {
        return rsaKey;
    }

    private static RSAKey loadOrCreate(Path path) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            if (Files.exists(path)) {
                log.info("auth: loaded RSA signing key from {}", path.toAbsolutePath());
                PrivateKey privateKey = keyFactory.generatePrivate(
                        new PKCS8EncodedKeySpec(decodePem(Files.readString(path))));
                return toRsaKey(keyFactory, privateKey);
            }

            log.warn("auth: no signing key at {} — generating one. Tokens issued before a restart "
                    + "will not verify after it; point this at a mounted secret in production.",
                    path.toAbsolutePath());

            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();

            Path absolute = path.toAbsolutePath();
            if (absolute.getParent() != null) {
                Files.createDirectories(absolute.getParent());
            }
            Files.writeString(absolute, encodePem(pair.getPrivate().getEncoded()));

            return toRsaKey(keyFactory, pair.getPrivate());
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot initialise the auth signing key", failure);
        }
    }

    private static RSAKey toRsaKey(KeyFactory keyFactory, PrivateKey privateKey) throws Exception {
        // The public exponent lives on the CRT form of an RSA private key. PKCS#8 keys produced
        // by KeyPairGenerator are CRT-encoded; anything else cannot yield its public half, so say
        // so loudly rather than producing a key that cannot be published.
        if (!(privateKey instanceof RSAPrivateCrtKey crtKey)) {
            throw new IllegalStateException(
                    "RSA private key must be PKCS#8/CRT-encoded to derive the public half for the JWKS");
        }

        RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));
        return new RSAKey.Builder(publicKey).privateKey(privateKey).keyIDFromThumbprint().build();
    }

    private static byte[] decodePem(String pem) {
        String body = pem.replace(BEGIN, "").replace(END, "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    private static String encodePem(byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(der);
        return BEGIN + "\n" + body + "\n" + END + "\n";
    }
}
