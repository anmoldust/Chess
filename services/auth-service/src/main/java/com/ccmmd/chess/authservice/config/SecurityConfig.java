package com.ccmmd.chess.authservice.config;

import com.ccmmd.chess.authservice.security.InternalTokenFilter;
import com.ccmmd.chess.authservice.security.ProblemWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * auth-service authenticates callers by checking credentials; it does not itself validate tokens
 * (that is the gateway's and every other service's job). So there is no session and no CSRF here —
 * callers are the gateway, which enforces both for the browser.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            @Value("${ccmmd.auth.internal-token}") String internalToken) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                // Reset is used by signed-out users by definition.
                                "/api/auth/password/forgot",
                                "/api/auth/password/reset",
                                "/.well-known/jwks.json",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/error")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new InternalTokenFilter(internalToken, objectMapper),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, failure) ->
                                ProblemWriter.write(objectMapper, response, 401, "Unauthorized",
                                        "Authentication required"))
                        .accessDeniedHandler((request, response, failure) ->
                                ProblemWriter.write(objectMapper, response, 403, "Forbidden",
                                        "You are not allowed to do that")));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
