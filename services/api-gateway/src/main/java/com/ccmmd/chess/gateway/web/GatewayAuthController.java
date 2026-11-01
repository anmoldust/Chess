package com.ccmmd.chess.gateway.web;

import com.ccmmd.chess.gateway.auth.AuthServiceClient;
import com.ccmmd.chess.gateway.auth.SessionKeys;
import com.ccmmd.chess.gateway.auth.SessionUser;
import com.ccmmd.chess.gateway.dto.AuthStatus;
import com.ccmmd.chess.gateway.dto.ForgotPasswordRequest;
import com.ccmmd.chess.gateway.dto.LoginRequest;
import com.ccmmd.chess.gateway.dto.PasswordResetResponse;
import com.ccmmd.chess.gateway.dto.RegisterRequest;
import com.ccmmd.chess.gateway.dto.ResetPasswordRequest;
import com.ccmmd.chess.gateway.dto.TokenResponse;
import com.ccmmd.chess.gateway.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Registration and login happen here, not by proxying blindly: the gateway has to turn a
 * successful credential check into a session, and hold the resulting token where the browser
 * cannot reach it.
 */
@RestController
@RequestMapping("/api/auth")
public class GatewayAuthController {

    private final AuthServiceClient authServiceClient;
    private final ServerSecurityContextRepository securityContextRepository;

    public GatewayAuthController(
            AuthServiceClient authServiceClient,
            ServerSecurityContextRepository securityContextRepository) {
        this.authServiceClient = authServiceClient;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<TokenResponse>> register(
            @Valid @RequestBody RegisterRequest request, ServerWebExchange exchange) {
        return authServiceClient.register(request)
                .flatMap(tokens -> establishSession(exchange, tokens))
                .map(tokens -> ResponseEntity.status(HttpStatus.CREATED).body(tokens));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request, ServerWebExchange exchange) {
        return authServiceClient.login(request)
                .flatMap(tokens -> establishSession(exchange, tokens))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/me")
    public Mono<AuthStatus> me() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .filter(SessionUser.class::isInstance)
                .cast(SessionUser.class)
                .map(user -> new AuthStatus(true, new UserDto(user.id(), user.email(), user.displayName())))
                .defaultIfEmpty(new AuthStatus(false, null));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(ServerWebExchange exchange) {
        // Invalidating the session drops the security context and the cached token together.
        return exchange.getSession()
                .flatMap(session -> session.invalidate())
                .thenReturn(ResponseEntity.noContent().build());
    }

    /** Starts a password reset. Always 202, so it cannot be used to discover registered accounts. */
    @PostMapping("/password/forgot")
    public Mono<ResponseEntity<PasswordResetResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return authServiceClient.forgotPassword(request)
                .map(response -> ResponseEntity.accepted().body(response));
    }

    @PostMapping("/password/reset")
    public Mono<ResponseEntity<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authServiceClient.resetPassword(request).thenReturn(ResponseEntity.noContent().build());
    }

    private Mono<TokenResponse> establishSession(ServerWebExchange exchange, TokenResponse tokens) {
        SessionUser user = new SessionUser(tokens.user().id(), tokens.user().email(), tokens.user().displayName());

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        SecurityContext context = new SecurityContextImpl(authentication);

        return securityContextRepository.save(exchange, context)
                .then(exchange.getSession().flatMap(session -> {
                    session.getAttributes().put(SessionKeys.ACCESS_TOKEN, tokens.token());
                    session.getAttributes().put(SessionKeys.ACCESS_TOKEN_EXPIRES_AT, tokens.expiresAt());
                    return session.save();
                }))
                .thenReturn(tokens);
    }
}
