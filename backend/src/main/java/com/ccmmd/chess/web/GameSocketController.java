package com.ccmmd.chess.web;

import com.ccmmd.chess.dto.MoveRequest;
import com.ccmmd.chess.dto.UserDto;
import com.ccmmd.chess.exception.GameConflictException;
import com.ccmmd.chess.exception.GameNotFoundException;
import com.ccmmd.chess.exception.IllegalMoveException;
import com.ccmmd.chess.service.AuthService;
import com.ccmmd.chess.service.GameEventPublisher;
import com.ccmmd.chess.service.GameService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP entry points for live play. The session cookie authenticates the handshake, so the
 * resolved {@link Principal} identifies the account; every handler re-validates through
 * {@link GameService} and broadcasts the result, or pushes a typed error to the error topic.
 */
@Controller
public class GameSocketController {

    private final GameService gameService;
    private final AuthService authService;
    private final GameEventPublisher publisher;

    public GameSocketController(GameService gameService, AuthService authService, GameEventPublisher publisher) {
        this.gameService = gameService;
        this.authService = authService;
        this.publisher = publisher;
    }

    @MessageMapping("/game/{id}/move")
    public void move(@DestinationVariable String id, @Payload MoveRequest request, Principal principal) {
        guard(id, () -> publisher.gameUpdated(gameService.makeMove(id, request, resolveUserId(principal))));
    }

    @MessageMapping("/game/{id}/join")
    public void join(@DestinationVariable String id, Principal principal) {
        UserDto user = resolveUser(principal);
        guard(id, () -> publisher.gameUpdated(gameService.joinGame(
                id, user == null ? null : user.id(), user == null ? null : user.displayName())));
    }

    @MessageMapping("/game/{id}/resign")
    public void resign(@DestinationVariable String id, Principal principal) {
        guard(id, () -> publisher.gameUpdated(gameService.resign(id, resolveUserId(principal))));
    }

    private UserDto resolveUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return authService.findByEmail(principal.getName()).orElse(null);
    }

    private String resolveUserId(Principal principal) {
        UserDto user = resolveUser(principal);
        return user == null ? null : user.id();
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
