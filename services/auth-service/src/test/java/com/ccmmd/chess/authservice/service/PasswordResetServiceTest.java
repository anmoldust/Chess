package com.ccmmd.chess.authservice.service;

import com.ccmmd.chess.authservice.domain.PasswordResetToken;
import com.ccmmd.chess.authservice.dto.LoginRequest;
import com.ccmmd.chess.authservice.dto.RegisterRequest;
import com.ccmmd.chess.authservice.exception.InvalidCredentialsException;
import com.ccmmd.chess.authservice.exception.InvalidResetTokenException;
import com.ccmmd.chess.authservice.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rolled back after each test. Token exposure is enabled in the test config so the raw token is
 * available to redeem — the one thing production hides.
 */
@SpringBootTest
@org.springframework.transaction.annotation.Transactional
class PasswordResetServiceTest {

    private static final String ORIGINAL = "original-password";
    private static final String REPLACEMENT = "replacement-password";

    @Autowired
    private PasswordResetService passwordReset;

    @Autowired
    private AccountService accounts;

    @Autowired
    private PasswordResetTokenRepository tokens;

    private String register(String email) {
        accounts.register(new RegisterRequest(email, ORIGINAL, "Player"));
        return email;
    }

    private static String email() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    @DisplayName("the token changes the password: the new one works and the old one does not")
    void resetChangesThePassword() {
        String email = register(email());
        String token = passwordReset.requestReset(email).orElseThrow();

        passwordReset.reset(token, REPLACEMENT);

        assertThat(accounts.login(new LoginRequest(email, REPLACEMENT))).isNotNull();
        assertThatThrownBy(() -> accounts.login(new LoginRequest(email, ORIGINAL)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("a reset link is single-use")
    void aTokenCannotBeRedeemedTwice() {
        String email = register(email());
        String token = passwordReset.requestReset(email).orElseThrow();

        passwordReset.reset(token, REPLACEMENT);

        assertThatThrownBy(() -> passwordReset.reset(token, "another-password"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    @DisplayName("requesting a reset for an unknown address stores nothing and reveals nothing")
    void unknownAddressIsIndistinguishable() {
        Optional<String> token = passwordReset.requestReset(email());

        assertThat(token).isEmpty();
        assertThat(tokens.count()).isZero();
    }

    @Test
    @DisplayName("an unknown token is rejected")
    void unknownTokenIsRejected() {
        assertThatThrownBy(() -> passwordReset.reset("not-a-real-token", REPLACEMENT))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    @DisplayName("an expired token is rejected")
    void expiredTokenIsRejected() {
        String email = register(email());
        String rawToken = "expired-token";
        tokens.saveAndFlush(new PasswordResetToken(
                UUID.randomUUID().toString(),
                // The account id is not needed to prove expiry is checked first.
                "some-user-id",
                PasswordResetService.sha256(rawToken),
                Instant.now().minusSeconds(60)));

        assertThatThrownBy(() -> passwordReset.reset(rawToken, REPLACEMENT))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    @DisplayName("a new request invalidates the previous link")
    void aNewRequestInvalidatesTheOldToken() {
        String email = register(email());
        String first = passwordReset.requestReset(email).orElseThrow();
        String second = passwordReset.requestReset(email).orElseThrow();

        assertThatThrownBy(() -> passwordReset.reset(first, REPLACEMENT))
                .isInstanceOf(InvalidResetTokenException.class);
        passwordReset.reset(second, REPLACEMENT);
    }
}
