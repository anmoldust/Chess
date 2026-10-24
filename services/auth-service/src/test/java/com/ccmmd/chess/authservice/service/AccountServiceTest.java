package com.ccmmd.chess.authservice.service;

import com.ccmmd.chess.authservice.domain.User;
import com.ccmmd.chess.authservice.dto.LoginRequest;
import com.ccmmd.chess.authservice.dto.RegisterRequest;
import com.ccmmd.chess.authservice.dto.TokenResponse;
import com.ccmmd.chess.authservice.exception.EmailAlreadyUsedException;
import com.ccmmd.chess.authservice.exception.InvalidCredentialsException;
import com.ccmmd.chess.authservice.repository.UserRepository;
import com.ccmmd.chess.platform.events.EventType;
import com.ccmmd.chess.platform.messaging.OutboxEntry;
import com.ccmmd.chess.platform.messaging.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AccountServiceTest {

    @Autowired
    private AccountService accounts;

    @Autowired
    private UserRepository users;

    @Autowired
    private OutboxRepository outbox;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    @DisplayName("registering creates the account and issues a token")
    void registerCreatesAccountAndToken() {
        String email = uniqueEmail();

        TokenResponse response = accounts.register(new RegisterRequest(email, "password123", "Alice"));

        assertThat(response.user().email()).isEqualTo(email);
        assertThat(response.user().displayName()).isEqualTo("Alice");
        assertThat(response.user().id()).isNotBlank();
        assertThat(response.token()).isNotBlank();
        assertThat(response.expiresAt()).isAfter(Instant.now());
        assertThat(users.findByEmailIgnoreCase(email)).isPresent();
    }

    @Test
    @DisplayName("the password is stored as a BCrypt hash, never in plain text")
    void passwordIsStoredHashed() {
        String email = uniqueEmail();

        accounts.register(new RegisterRequest(email, "password123", "Bob"));

        User stored = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo("password123");
        assertThat(stored.getPasswordHash()).startsWith("$2");
    }

    @Test
    @DisplayName("registering queues a user.registered event in the same transaction")
    void registerQueuesUserRegisteredEvent() {
        long before = outbox.count();

        accounts.register(new RegisterRequest(uniqueEmail(), "password123", "Carol"));

        assertThat(outbox.count()).isGreaterThan(before);
        assertThat(outbox.findAll())
                .extracting(OutboxEntry::getType)
                .contains(EventType.USER_REGISTERED);
    }

    @Test
    @DisplayName("the same email cannot be registered twice")
    void duplicateEmailIsRejected() {
        String email = uniqueEmail();
        accounts.register(new RegisterRequest(email, "password123", "Dan"));

        assertThatThrownBy(() -> accounts.register(new RegisterRequest(email, "password123", "Dan Again")))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    @DisplayName("email is normalised, so casing does not create a second account")
    void emailIsCaseInsensitive() {
        String email = uniqueEmail();
        accounts.register(new RegisterRequest(email, "password123", "Erin"));

        assertThatThrownBy(() -> accounts.register(
                new RegisterRequest(email.toUpperCase(java.util.Locale.ROOT), "password123", "Erin Again")))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    @DisplayName("login with the correct password succeeds")
    void loginSucceeds() {
        String email = uniqueEmail();
        accounts.register(new RegisterRequest(email, "password123", "Frank"));

        TokenResponse login = accounts.login(new LoginRequest(email, "password123"));

        assertThat(login.user().email()).isEqualTo(email);
        assertThat(login.token()).isNotBlank();
    }

    @Test
    @DisplayName("login with the wrong password is rejected")
    void loginWithWrongPasswordIsRejected() {
        String email = uniqueEmail();
        accounts.register(new RegisterRequest(email, "password123", "Grace"));

        assertThatThrownBy(() -> accounts.login(new LoginRequest(email, "not-the-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("login for an unknown account is rejected the same way as a bad password")
    void loginForUnknownAccountIsRejected() {
        assertThatThrownBy(() -> accounts.login(new LoginRequest(uniqueEmail(), "password123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("refresh issues a token for an existing account")
    void refreshIssuesToken() {
        TokenResponse registered = accounts.register(new RegisterRequest(uniqueEmail(), "password123", "Heidi"));

        TokenResponse refreshed = accounts.refresh(registered.user().id());

        assertThat(refreshed.user().id()).isEqualTo(registered.user().id());
        assertThat(refreshed.token()).isNotBlank();
    }
}
