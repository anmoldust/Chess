package com.ccmmd.chess.gateway.config;

import com.ccmmd.chess.gateway.web.ReactiveProblems;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * The browser-facing security surface, and the only place with a session.
 *
 * <p>The browser holds an httpOnly {@code SESSION} cookie and a readable {@code XSRF-TOKEN}
 * cookie; no token is ever exposed to JavaScript. Downstream services get a short-lived JWT
 * instead (see {@code DownstreamAuthFilter}), so they stay stateless.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveProblems problems,
            ServerSecurityContextRepository securityContextRepository) {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler()))
                .securityContextRepository(securityContextRepository)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/me",
                                // A user who forgot their password is signed out by definition.
                                "/api/auth/password/forgot",
                                "/api/auth/password/reset",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/error")
                        .permitAll()
                        // Games are watchable without an account, exactly as before the split.
                        .pathMatchers(HttpMethod.GET, "/api/games", "/api/games/**").permitAll()
                        // A report about a watchable game is equally safe to read. Each service
                        // enforces its own rule too; this is the edge deciding what it will even
                        // forward, and a 401 here is indistinguishable to a caller from a
                        // downstream rejection.
                        .pathMatchers(HttpMethod.GET, "/api/analysis/**").permitAll()
                        .anyExchange().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((exchange, failure) -> problems.write(
                                exchange.getResponse(), HttpStatus.UNAUTHORIZED, "Unauthorized",
                                "Authentication required"))
                        .accessDeniedHandler((exchange, failure) -> problems.write(
                                exchange.getResponse(), HttpStatus.FORBIDDEN, "Forbidden",
                                "You are not allowed to do that")));
        return http.build();
    }

    @Bean
    public ServerSecurityContextRepository securityContextRepository() {
        return new WebSessionServerSecurityContextRepository();
    }

    /**
     * Spring Security writes the CSRF cookie lazily, only when the token is read. Without this the
     * very first mutation from a fresh browser would be rejected for a token it was never given.
     */
    @Bean
    public WebFilter csrfCookieWebFilter() {
        return (exchange, chain) -> {
            // The attribute is itself a Mono<CsrfToken>; subscribing (via the then() chain) is what
            // makes Spring Security generate the token and write the cookie.
            Mono<CsrfToken> csrfToken =
                    exchange.getAttributeOrDefault(CsrfToken.class.getName(), Mono.<CsrfToken>empty());
            return csrfToken.then(chain.filter(exchange));
        };
    }
}
