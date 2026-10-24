package com.ccmmd.chess.analysisservice.analysis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Reads a finished game from game-service.
 *
 * <p>{@code GET /api/games/**} is public on game-service (games are watchable), so this needs no
 * token. If that ever changes, this call is the single place that has to learn to authenticate.
 */
@Component
public class GameClient {

    private final WebClient webClient;
    private final Duration timeout;

    public GameClient(
            WebClient.Builder builder,
            @Value("${ccmmd.services.game-service:http://localhost:8082}") String gameServiceUrl,
            @Value("${ccmmd.services.timeout:PT5S}") Duration timeout) {
        this.webClient = builder.baseUrl(gameServiceUrl).build();
        this.timeout = timeout;
    }

    public Mono<GameSnapshot> fetch(String gameId) {
        return webClient.get()
                .uri("/api/games/{id}", gameId)
                .retrieve()
                .bodyToMono(RemoteGame.class)
                .timeout(timeout)
                .map(remote -> new GameSnapshot(
                        remote.id(),
                        remote.status(),
                        remote.result(),
                        remote.whiteUserId(),
                        remote.blackUserId(),
                        remote.moves() == null
                                ? List.of()
                                : remote.moves().stream().map(RemoteMove::fenAfter).toList()));
    }

    private record RemoteGame(
            String id, String status, String result,
            String whiteUserId, String blackUserId, List<RemoteMove> moves) {
    }

    private record RemoteMove(int ply, String fenAfter) {
    }
}
