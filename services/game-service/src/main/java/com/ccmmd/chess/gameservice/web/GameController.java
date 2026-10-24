package com.ccmmd.chess.gameservice.web;

import com.ccmmd.chess.gameservice.domain.GameStatus;
import com.ccmmd.chess.gameservice.dto.CreateGameRequest;
import com.ccmmd.chess.gameservice.dto.GameDto;
import com.ccmmd.chess.gameservice.dto.MoveRequest;
import com.ccmmd.chess.gameservice.security.JwtPrincipals;
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

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping
    public ResponseEntity<GameDto> create(
            @RequestBody(required = false) CreateGameRequest request,
            Authentication authentication) {
        GameDto game = gameService.createGame(
                request, JwtPrincipals.userId(authentication), JwtPrincipals.displayName(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(game);
    }

    @GetMapping
    public List<GameDto> list(@RequestParam(required = false) GameStatus status) {
        return gameService.listGames(status);
    }

    @GetMapping("/{id}")
    public GameDto get(@PathVariable String id) {
        return gameService.getGame(id);
    }

    @PostMapping("/{id}/join")
    public GameDto join(@PathVariable String id, Authentication authentication) {
        return gameService.joinGame(
                id, JwtPrincipals.userId(authentication), JwtPrincipals.displayName(authentication));
    }

    @PostMapping("/{id}/move")
    public GameDto move(
            @PathVariable String id,
            @RequestBody MoveRequest request,
            Authentication authentication) {
        return gameService.makeMove(id, request, JwtPrincipals.userId(authentication));
    }

    @PostMapping("/{id}/resign")
    public GameDto resign(@PathVariable String id, Authentication authentication) {
        return gameService.resign(id, JwtPrincipals.userId(authentication));
    }
}
