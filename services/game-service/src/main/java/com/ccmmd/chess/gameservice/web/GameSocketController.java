package com.ccmmd.chess.gameservice.web;

import com.ccmmd.chess.gameservice.dto.MoveRequest;
import com.ccmmd.chess.gameservice.exception.GameConflictException;
import com.ccmmd.chess.gameservice.exception.GameNotFoundException;
import com.ccmmd.chess.gameservice.exception.IllegalMoveException;
import com.ccmmd.chess.gameservice.security.JwtPrincipals;
import com.ccmmd.chess.gameservice.service.GameEventPublisher;
import com.ccmmd.chess.gameservice.service.GameService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP entry points for live play. The gateway attaches the Bearer token to the handshake, so
 * the resolved {@link Principal} is the verified JWT — the identity is never taken from the
 * message body.
 */
@Controller
public class GameSocketController {

    private final GameService gameService;
    private final GameEventPublisher publisher;

    public GameSocketController(GameService gameService, GameEventPublisher publisher) {
        this.gameService = gameService;
        this.publisher = publisher;
    }

    @MessageMapping("/game/{id}/move")
    public void move(@DestinationVariable String id, @Payload MoveRequest request, Principal principal) {
        guard(id, () -> publisher.gameUpdated(
                gameService.makeMove(id, request, JwtPrincipals.userId(principal))));
    }

    @MessageMapping("/game/{id}/join")
    public void join(@DestinationVariable String id, Principal principal) {
        guard(id, () -> publisher.gameUpdated(gameService.joinGame(
                id, JwtPrincipals.userId(principal), JwtPrincipals.displayName(principal))));
    }

    @MessageMapping("/game/{id}/resign")
    public void resign(@DestinationVariable String id, Principal principal) {
        guard(id, () -> publisher.gameUpdated(gameService.resign(id, JwtPrincipals.userId(principal))));
    }

    private void guard(String gameId, Runnable action) {
        try {
            action.run();
        } catch (GameNotFoundException ex) {
            publisher.reject(gameId, "GAME_NOT_FOUND", ex.getMessage());
        } catch (IllegalMoveException ex) {
            publisher.reject(gameId, "ILLEGAL_MOVE", ex.getMessage());
        } catch (GameConflictException ex) {
            publisher.reject(gameId, "CONFLICT", ex.getMessage());
        } catch (AccessDeniedException ex) {
            publisher.reject(gameId, "FORBIDDEN", ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            publisher.reject(gameId, "CONFLICT", "Concurrent modification; retry the move.");
        } catch (IllegalArgumentException ex) {
            publisher.reject(gameId, "BAD_REQUEST", ex.getMessage());
        }
    }
}
