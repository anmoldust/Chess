package com.ccmmd.chess.gameservice.web;

import com.ccmmd.chess.gameservice.domain.GameStatus;
import com.ccmmd.chess.gameservice.dto.CreateGameRequest;
import com.ccmmd.chess.gameservice.dto.GameDto;
import com.ccmmd.chess.gameservice.dto.GameSummaryDto;
import com.ccmmd.chess.gameservice.dto.MoveRequest;
import com.ccmmd.chess.gameservice.security.JwtPrincipals;
import com.ccmmd.chess.gameservice.service.GameEventPublisher;
import com.ccmmd.chess.gameservice.service.GameService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST entry points for a game.
 *
 * <p>Every mutation broadcasts the new state to the game's topic. A client that did not make the
 * change learns of it <em>only</em> from that broadcast, and a draw offer is always made over REST —
 * so without the broadcast an opponent would never see the offer. Publishing here, after the
 * service method has returned, is deliberate: its transaction has committed by then, so a
 * subscriber can never be told about state that then rolled back.
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;
    private final GameEventPublisher publisher;

    public GameController(GameService gameService, GameEventPublisher publisher) {
        this.gameService = gameService;
        this.publisher = publisher;
    }

    @PostMapping
    public ResponseEntity<GameDto> create(
            @RequestBody(required = false) CreateGameRequest request,
            Authentication authentication) {
        GameDto game = gameService.createGame(
                request, JwtPrincipals.userId(authentication), JwtPrincipals.displayName(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(broadcast(game));
    }

    @GetMapping
    public List<GameDto> list(@RequestParam(required = false) GameStatus status) {
        return gameService.listGames(status);
    }

    /** A player's finished games, newest first. Public — games are watchable here. */
    @GetMapping("/history")
    public List<GameSummaryDto> history(
            @RequestParam String userId,
            @RequestParam(defaultValue = "20") int limit) {
        return gameService.history(userId, limit);
    }

    @GetMapping("/{id}")
    public GameDto get(@PathVariable String id) {
        return gameService.getGame(id);
    }

    @PostMapping("/{id}/join")
    public GameDto join(@PathVariable String id, Authentication authentication) {
        return broadcast(gameService.joinGame(
                id, JwtPrincipals.userId(authentication), JwtPrincipals.displayName(authentication)));
    }

    @PostMapping("/{id}/move")
    public GameDto move(
            @PathVariable String id,
            @RequestBody MoveRequest request,
            Authentication authentication) {
        return broadcast(gameService.makeMove(id, request, JwtPrincipals.userId(authentication)));
    }

    @PostMapping("/{id}/resign")
    public GameDto resign(@PathVariable String id, Authentication authentication) {
        return broadcast(gameService.resign(id, JwtPrincipals.userId(authentication)));
    }

    /** Offers a draw, or accepts the opponent's open offer. */
    @PostMapping("/{id}/draw")
    public GameDto offerDraw(@PathVariable String id, Authentication authentication) {
        return broadcast(gameService.offerDraw(id, JwtPrincipals.userId(authentication)));
    }

    @PostMapping("/{id}/draw/decline")
    public GameDto declineDraw(@PathVariable String id, Authentication authentication) {
        return broadcast(gameService.declineDraw(id, JwtPrincipals.userId(authentication)));
    }

    /** Offers a rematch, or accepts the opponent's open offer. */
    @PostMapping("/{id}/rematch")
    public GameDto rematch(@PathVariable String id, Authentication authentication) {
        return broadcast(gameService.rematch(id, JwtPrincipals.userId(authentication)));
    }

    /** Requests a takeback of the last move, or accepts the opponent's open request. */
    @PostMapping("/{id}/takeback")
    public GameDto takeback(@PathVariable String id, Authentication authentication) {
        return broadcast(gameService.takeback(id, JwtPrincipals.userId(authentication)));
    }

    @PostMapping("/{id}/takeback/decline")
    public GameDto declineTakeback(@PathVariable String id, Authentication authentication) {
        return broadcast(gameService.declineTakeback(id, JwtPrincipals.userId(authentication)));
    }

    /** Publishes the new state to the game's topic, then returns it. */
    private GameDto broadcast(GameDto game) {
        publisher.gameUpdated(game);
        return game;
    }
}
