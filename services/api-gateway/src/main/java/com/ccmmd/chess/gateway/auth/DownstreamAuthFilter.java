package com.ccmmd.chess.gateway.auth;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Turns the browser session into an identity for the services.
 *
 * <p>Ordering matters twice over. It must run <em>after</em> the security chain (so an
 * authenticated context exists) and <em>after</em> Spring Session has decorated the exchange (so
 * {@code getSession()} is the Redis-backed one). It must run <em>before</em> the gateway's routing
 * handler, which any {@link WebFilter} does.
 *
 * <p>It also strips any inbound {@code Authorization} / {@code X-User-*} header before setting its
 * own: a client must not be able to assert an identity by adding a header.
 */
@Component
public class DownstreamAuthFilter implements WebFilter, Ordered {

    /** Refresh this long before expiry, so a request never races the clock. */
    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(2);

    private final AuthServiceClient authServiceClient;

    public DownstreamAuthFilter(AuthServiceClient authServiceClient) {
        this.authServiceClient = authServiceClient;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!isProxied(path)) {
            return chain.filter(exchange);
        }

        ServerWebExchange sanitised = stripClientIdentity(exchange);

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(authentication -> authentication.isAuthenticated()
                        && authentication.getPrincipal() instanceof SessionUser)
                .flatMap(authentication -> accessTokenFor(sanitised, (SessionUser) authentication.getPrincipal()))
                .map(token -> withBearer(sanitised, token))
                .defaultIfEmpty(sanitised)
                .flatMap(chain::filter);
    }

    /**
     * Everything the gateway does not handle itself is proxied.
     *
     * <p>This was originally an allow-list of prefixes ({@code /api/games}, {@code /ws}) and it
     * silently rotted: adding the matchmaking and presence routes meant those requests were
     * forwarded with no Bearer token and every call came back 401. Inverting the test means a new
     * route inherits identity without anyone remembering to register it here.
     */
    private static boolean isProxied(String path) {
        return !(path.startsWith("/api/auth")
                || path.startsWith("/actuator")
                || path.startsWith("/error"));
    }

    private static ServerWebExchange stripClientIdentity(ServerWebExchange exchange) {
        var sanitised = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(HttpHeaders.AUTHORIZATION);
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Email");
                    headers.remove("X-Internal-Token");
                })
                .build();
        return exchange.mutate().request(sanitised).build();
    }

    private Mono<String> accessTokenFor(ServerWebExchange exchange, SessionUser user) {
        return exchange.getSession().flatMap(session -> {
            Object token = session.getAttributes().get(SessionKeys.ACCESS_TOKEN);
            Object expiresAt = session.getAttributes().get(SessionKeys.ACCESS_TOKEN_EXPIRES_AT);

            if (token instanceof String value
                    && expiresAt instanceof Instant expiry
                    && Instant.now().isBefore(expiry.minus(REFRESH_MARGIN))) {
                return Mono.just(value);
            }

            return authServiceClient.refreshFor(user.id()).flatMap(refreshed -> {
                session.getAttributes().put(SessionKeys.ACCESS_TOKEN, refreshed.token());
                session.getAttributes().put(SessionKeys.ACCESS_TOKEN_EXPIRES_AT, refreshed.expiresAt());
                return session.save().thenReturn(refreshed.token());
            });
        });
    }

    private static ServerWebExchange withBearer(ServerWebExchange exchange, String token) {
        var authorised = exchange.getRequest().mutate()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        return exchange.mutate().request(authorised).build();
    }
}
