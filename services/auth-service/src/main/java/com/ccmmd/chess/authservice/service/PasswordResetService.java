package com.ccmmd.chess.authservice.service;

import com.ccmmd.chess.authservice.domain.PasswordResetToken;
import com.ccmmd.chess.authservice.domain.User;
import com.ccmmd.chess.authservice.exception.InvalidResetTokenException;
import com.ccmmd.chess.authservice.repository.PasswordResetTokenRepository;
import com.ccmmd.chess.authservice.repository.UserRepository;
import com.ccmmd.chess.authservice.security.ResetNotifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Password reset: request a single-use link, redeem it for a new password.
 *
 * <p>Two properties are the point of the design. Requesting a reset <em>always</em> looks the same
 * to the caller, whether or not the address is registered, so it is not an account-enumeration
 * oracle — the same reasoning that makes login return one failure for both "no such account" and
 * "wrong password". And redemption is single-use and time-limited, so a leaked or replayed link is
 * worthless.
 */
@Service
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final ResetNotifier notifier;
    private final Duration ttl;
    private final String resetBaseUrl;
    private final boolean exposeToken;

    public PasswordResetService(
            UserRepository users,
            PasswordResetTokenRepository tokens,
            PasswordEncoder passwordEncoder,
            ResetNotifier notifier,
            @Value("${ccmmd.auth.reset.ttl:PT30M}") Duration ttl,
            @Value("${ccmmd.auth.reset.base-url:http://localhost:3000/#/reset}") String resetBaseUrl,
            @Value("${ccmmd.auth.reset.expose-token:false}") boolean exposeToken) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.notifier = notifier;
        this.ttl = ttl;
        this.resetBaseUrl = resetBaseUrl;
        this.exposeToken = exposeToken;
    }

    /**
     * Starts a reset. Returns the raw token only when token exposure is enabled (local development);
     * in every real deployment it returns empty and the link goes to the notifier.
     */
    @Transactional
    public Optional<String> requestReset(String email) {
        String normalised = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        User user = users.findByEmailIgnoreCase(normalised).orElse(null);
        if (user == null) {
            // Identical response to the "found" path — nothing here may reveal whether it exists.
            return Optional.empty();
        }

        // One live link per account: issuing a new one invalidates the previous.
        tokens.deleteByUserId(user.getId());

        String rawToken = generateToken();
        tokens.saveAndFlush(new PasswordResetToken(
                UUID.randomUUID().toString(),
                user.getId(),
                sha256(rawToken),
                Instant.now().plus(ttl)));

        notifier.deliver(user.getEmail(), resetUrl(rawToken), ttl);
        return exposeToken ? Optional.of(rawToken) : Optional.empty();
    }

    /** Redeems a token for a new password and burns it. */
    @Transactional
    public void reset(String rawToken, String newPassword) {
        PasswordResetToken token = tokens.findByTokenHash(sha256(rawToken))
                .filter(candidate -> candidate.isUsableAt(Instant.now()))
                .orElseThrow(() -> new InvalidResetTokenException("This reset link is invalid or has expired"));

        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new InvalidResetTokenException("This reset link is invalid or has expired"));

        user.changePasswordHash(passwordEncoder.encode(newPassword));
        token.markUsed();

        users.saveAndFlush(user);
        tokens.save(token);
    }

    private String resetUrl(String rawToken) {
        return resetBaseUrl + "?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the platform", impossible);
        }
    }
}
