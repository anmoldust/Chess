package com.ccmmd.chess.web;

import com.ccmmd.chess.domain.GameStatus;
import com.ccmmd.chess.dto.CreateGameRequest;
import com.ccmmd.chess.dto.GameDto;
import com.ccmmd.chess.dto.MoveRequest;
import com.ccmmd.chess.security.CurrentUser;
import com.ccmmd.chess.service.GameService;
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
                request, CurrentUser.id(authentication), CurrentUser.displayName(authentication));
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
        return gameService.joinGame(id, CurrentUser.id(authentication), CurrentUser.displayName(authentication));
    }

    @PostMapping("/{id}/move")
    public GameDto move(
            @PathVariable String id,
            @RequestBody MoveRequest request,
            Authentication authentication) {
        return gameService.makeMove(id, request, CurrentUser.id(authentication));
    }

    @PostMapping("/{id}/resign")
    public GameDto resign(@PathVariable String id, Authentication authentication) {
        return gameService.resign(id, CurrentUser.id(authentication));
    }
}
