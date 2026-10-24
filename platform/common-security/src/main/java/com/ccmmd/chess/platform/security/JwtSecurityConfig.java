package com.ccmmd.chess.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.Locale;

/**
 * Every service is a stateless OAuth2 resource server: it trusts only JWTs signed by the auth
 * service, verified against the published JWKS. There are no sessions and no CSRF tokens here —
 * those live at the gateway, which is the only thing a browser talks to.
 */
@Configuration
@EnableWebSecurity
public class JwtSecurityConfig {

    @Bean
    public SecurityFilterChain internalApiChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            @Value("${ccmmd.security.public-paths:}") List<String> publicPaths) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll();
                    publicPaths.forEach(entry -> permit(auth, entry));
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, exception) ->
                                ProblemResponses.write(objectMapper, response, 401, "Unauthorized",
                                        "A valid access token is required")))
                .exceptionHandling(handling -> handling
                        .accessDeniedHandler((request, response, exception) ->
                                ProblemResponses.write(objectMapper, response, 403, "Forbidden",
                                        "You are not allowed to do that")));
        return http.build();
    }

    /**
     * Declares one public path. Supports an optional {@code METHOD:pattern} form so a service can
     * open reads without opening writes — e.g. {@code GET:/api/games/**}. Permitting a path at the
     * edge does not make it safe: services still decide identity and authorisation from the token,
     * and a mutation with no caller is rejected there.
     */
    private static void permit(
            org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<HttpSecurity>
                    .AuthorizationManagerRequestMatcherRegistry auth,
            String entry) {
        if (entry == null || entry.isBlank()) {
            return;
        }
        String trimmed = entry.trim();
        int separator = trimmed.indexOf(':');
        if (separator > 0) {
            HttpMethod method = HttpMethod.valueOf(trimmed.substring(0, separator).trim().toUpperCase(Locale.ROOT));
            auth.requestMatchers(method, trimmed.substring(separator + 1).trim()).permitAll();
        } else {
            auth.requestMatchers(trimmed).permitAll();
        }
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${ccmmd.security.jwks-uri:http://localhost:8081/.well-known/jwks.json}") String jwksUri,
            @Value("${ccmmd.security.issuer:ccmmd-auth}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();

        OAuth2TokenValidator<Jwt> validator = issuer == null || issuer.isBlank()
                ? JwtValidators.createDefault()
                : JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validator));

        return decoder;
    }
}
