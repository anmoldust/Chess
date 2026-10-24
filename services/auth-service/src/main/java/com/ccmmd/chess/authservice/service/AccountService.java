package com.ccmmd.chess.authservice.service;

import com.ccmmd.chess.authservice.domain.User;
import com.ccmmd.chess.authservice.dto.LoginRequest;
import com.ccmmd.chess.authservice.dto.RegisterRequest;
import com.ccmmd.chess.authservice.dto.TokenResponse;
import com.ccmmd.chess.authservice.dto.UserDto;
import com.ccmmd.chess.authservice.exception.EmailAlreadyUsedException;
import com.ccmmd.chess.authservice.exception.InvalidCredentialsException;
import com.ccmmd.chess.authservice.repository.UserRepository;
import com.ccmmd.chess.authservice.security.LoginRateLimiter;
import com.ccmmd.chess.authservice.security.TokenIssuer;
import com.ccmmd.chess.platform.events.EventType;
import com.ccmmd.chess.platform.events.payload.UserRegistered;
import com.ccmmd.chess.platform.messaging.OutboxPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AccountService {

    private static final String PRODUCER = "auth-service";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final OutboxPublisher outbox;
    private final LoginRateLimiter loginRateLimiter;

    public AccountService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            TokenIssuer tokenIssuer,
            OutboxPublisher outbox,
            LoginRateLimiter loginRateLimiter) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.outbox = outbox;
        this.loginRateLimiter = loginRateLimiter;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = normaliseEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyUsedException("An account already exists for " + email);
        }

        User user = new User(
                UUID.randomUUID().toString(),
                email,
                request.displayName().trim(),
                passwordEncoder.encode(request.password()));

        users.saveAndFlush(user);

        // Same transaction as the insert: the outbox row and the account commit together, so the
        // welcome event can never be published for an account that rolled back.
        outbox.publish(
                EventType.USER_REGISTERED,
                PRODUCER,
                user.getId(),
                null,
                new UserRegistered(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt()));

        return issueTokenFor(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        String email = normaliseEmail(request.email());
        loginRateLimiter.checkAllowed(email);

        User user = users.findByEmailIgnoreCase(email).orElse(null);

        // One failure path for both "no such account" and "wrong password": telling the caller
        // which half was wrong turns login into an account-enumeration oracle.
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginRateLimiter.recordFailure(email);
            throw new InvalidCredentialsException("Invalid email or password");
        }

        loginRateLimiter.reset(email);
        return issueTokenFor(user);
    }

    /** Called by the gateway, authorised by the internal service token. */
    @Transactional(readOnly = true)
    public TokenResponse refresh(String userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("Unknown account " + userId));
        return issueTokenFor(user);
    }

    private TokenResponse issueTokenFor(User user) {
        UserDto dto = new UserDto(user.getId(), user.getEmail(), user.getDisplayName());
        TokenIssuer.IssuedToken issued = tokenIssuer.issue(dto);
        return new TokenResponse(dto, issued.token(), issued.expiresAt());
    }

    private static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
