package com.ccmmd.chess.gateway.auth;

import com.ccmmd.chess.gateway.dto.LoginRequest;
import com.ccmmd.chess.gateway.dto.RegisterRequest;
import com.ccmmd.chess.gateway.dto.TokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * The gateway's only outbound dependency for identity. It never talks to the user table itself —
 * auth-service owns accounts, and this keeps it that way.
 */
@Component
public class AuthServiceClient {

    private final WebClient webClient;
    private final String internalToken;

    public AuthServiceClient(
            WebClient.Builder builder,
            @Value("${ccmmd.services.auth-service}") String authServiceBaseUrl,
            @Value("${ccmmd.internal-token}") String internalToken) {
        this.webClient = builder.baseUrl(authServiceBaseUrl).build();
        this.internalToken = internalToken;
    }

    public Mono<TokenResponse> register(RegisterRequest request) {
        return post("/api/auth/register", request, false);
    }

    public Mono<TokenResponse> login(LoginRequest request) {
        return post("/api/auth/login", request, false);
    }

    /**
     * Mints a fresh token for a user the gateway has already authenticated. This is why it needs
     * the internal service token: the caller is the gateway, not the user.
     */
    public Mono<TokenResponse> refreshFor(String userId) {
        return post("/api/auth/refresh", Map.of("userId", userId), true);
    }

    private Mono<TokenResponse> post(String path, Object body, boolean internal) {
        WebClient.RequestBodySpec spec = webClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON);

        if (internal) {
            spec = spec.header("X-Internal-Token", internalToken);
        }

        return spec.bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response
                        .bodyToMono(ProblemBody.class)
                        .defaultIfEmpty(new ProblemBody(null, null))
                        .flatMap(problem -> Mono.error(
                                new DownstreamProblem(response.statusCode(), problem.title(), problem.detail()))))
                .bodyToMono(TokenResponse.class);
    }

    private record ProblemBody(String title, String detail) {
    }
}
