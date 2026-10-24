package com.ccmmd.chess.service;

import com.ccmmd.chess.domain.GameMode;
import com.ccmmd.chess.domain.GameStatus;
import com.ccmmd.chess.domain.PlayerColor;
import com.ccmmd.chess.dto.CreateGameRequest;
import com.ccmmd.chess.dto.GameDto;
import com.ccmmd.chess.dto.MoveRequest;
import com.ccmmd.chess.exception.GameConflictException;
import com.ccmmd.chess.exception.GameNotFoundException;
import com.ccmmd.chess.exception.IllegalMoveException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class GameServiceTest {

    private static final String RAJAT = "user-rajat";
    private static final String SAM = "user-sam";
    private static final String ALEX = "user-alex";

    @Autowired
    private GameService gameService;

    private GameDto newHumanGame(String userId, String name) {
        return gameService.createGame(
                new CreateGameRequest(GameMode.HUMAN_VS_HUMAN, null, PlayerColor.WHITE), userId, name);
    }

    private GameDto newAiGame(String userId, String name, PlayerColor color) {
        return gameService.createGame(new CreateGameRequest(GameMode.HUMAN_VS_AI, null, color), userId, name);
    }

    @Test
    @DisplayName("a human-vs-human game starts waiting, owned by the creator")
    void createHumanGame() {
        GameDto game = newHumanGame(RAJAT, "rajat");
        assertThat(game.status()).isEqualTo(GameStatus.WAITING_FOR_OPPONENT);
        assertThat(game.whitePlayer()).isEqualTo("rajat");
        assertThat(game.whiteUserId()).isEqualTo(RAJAT);
        assertThat(game.blackPlayer()).isNull();
        assertThat(game.blackUserId()).isNull();
        assertThat(game.fen()).isEqualTo(ChessRules.START_FEN);
    }

    @Test
    @DisplayName("an AI game starts in progress with the computer unowned")
    void createAiGame() {
        GameDto game = newAiGame(RAJAT, "rajat", PlayerColor.BLACK);
        assertThat(game.status()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(game.blackUserId()).isEqualTo(RAJAT);
        assertThat(game.whitePlayer()).isEqualTo("Stockfish");
        assertThat(game.whiteUserId()).isNull();
        assertThat(game.aiColor()).isEqualTo(PlayerColor.WHITE);
    }

    @Test
    @DisplayName("creating a game requires a signed-in user")
    void createRequiresAuth() {
        assertThatThrownBy(() -> gameService.createGame(
                new CreateGameRequest(GameMode.HUMAN_VS_HUMAN, null, PlayerColor.WHITE), null, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("an opponent can take the open seat")
    void joinGame() {
        GameDto game = newHumanGame(RAJAT, "rajat");
        GameDto joined = gameService.joinGame(game.id(), SAM, "sam");
        assertThat(joined.status()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(joined.blackPlayer()).isEqualTo("sam");
        assertThat(joined.blackUserId()).isEqualTo(SAM);
    }

    @Test
    @DisplayName("you cannot join a game you are already in")
    void joinOwnGameRejected() {
        GameDto game = newHumanGame(RAJAT, "rajat");
        assertThatThrownBy(() -> gameService.joinGame(game.id(), RAJAT, "rajat"))
                .isInstanceOf(GameConflictException.class);
    }

    @Test
    @DisplayName("a full game cannot be joined")
    void joinFullGameRejected() {
        GameDto game = newHumanGame(RAJAT, "rajat");
        gameService.joinGame(game.id(), SAM, "sam");
        assertThatThrownBy(() -> gameService.joinGame(game.id(), ALEX, "alex"))
                .isInstanceOf(GameConflictException.class);
    }

    @Test
    @DisplayName("an AI game is not joinable")
    void joinAiGameRejected() {
        GameDto game = newAiGame(RAJAT, "rajat", PlayerColor.WHITE);
        assertThatThrownBy(() -> gameService.joinGame(game.id(), SAM, "sam"))
                .isInstanceOf(GameConflictException.class);
    }

    @Test
    @DisplayName("a legal move from the owner advances the board")
    void legalMoveAdvancesGame() {
        GameDto game = newAiGame(RAJAT, "rajat", PlayerColor.WHITE);
        GameDto after = gameService.makeMove(game.id(), new MoveRequest("e2", "e4", null), RAJAT);

        assertThat(after.moves()).hasSize(1);
        assertThat(after.moves().get(0).uci()).isEqualTo("e2e4");
        assertThat(after.turn()).isEqualTo(PlayerColor.BLACK);
        assertThat(after.version()).isGreaterThan(game.version());
    }

    @Test
    @DisplayName("an illegal move is rejected and leaves the board untouched")
    void illegalMoveRejected() {
        GameDto game = newAiGame(RAJAT, "rajat", PlayerColor.WHITE);
        assertThatThrownBy(() -> gameService.makeMove(game.id(), new MoveRequest("e2", "e5", null), RAJAT))
                .isInstanceOf(IllegalMoveException.class);
        assertThat(gameService.getGame(game.id()).moves()).isEmpty();
    }

    @Test
    @DisplayName("moves may not be played before the game starts")
    void moveBeforeStartRejected() {
        GameDto game = newHumanGame(RAJAT, "rajat");
        assertThatThrownBy(() -> gameService.makeMove(game.id(), new MoveRequest("e2", "e4", null), RAJAT))
                .isInstanceOf(GameConflictException.class);
    }

    @Test
    @DisplayName("a signed-out caller cannot move")
    void anonymousMoveRejected() {
        GameDto game = newAiGame(RAJAT, "rajat", PlayerColor.WHITE);
        assertThatThrownBy(() -> gameService.makeMove(game.id(), new MoveRequest("e2", "e4", null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("in a two-player game, only the side to move may move")
    void outOfTurnMoveRejected() {
        GameDto game = newHumanGame(RAJAT, "rajat");
        GameDto joined = gameService.joinGame(game.id(), SAM, "sam");
        // It is white (rajat) to move; sam may not move for white.
        assertThatThrownBy(() -> gameService.makeMove(joined.id(), new MoveRequest("e2", "e4", null), SAM))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("an unrelated account cannot move in someone else's game")
    void unrelatedUserCannotMove() {
        GameDto game = newAiGame(RAJAT, "rajat", PlayerColor.WHITE);
        assertThatThrownBy(() -> gameService.makeMove(game.id(), new MoveRequest("e2", "e4", null), ALEX))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("the human owner may submit the computer's replies in an AI game")
    void aiGameAllowsOwnerToMoveBothColors() {
        GameDto game = newAiGame(RAJAT, "rajat", PlayerColor.WHITE);
        GameDto afterWhite = gameService.makeMove(game.id(), new MoveRequest("e2", "e4", null), RAJAT);
        assertThat(afterWhite.turn()).isEqualTo(PlayerColor.BLACK);

        // The owner's client drives the engine, so submitting black's reply is allowed.
        GameDto afterBlack = gameService.makeMove(game.id(), new MoveRequest("e7", "e5", null), RAJAT);
        assertThat(afterBlack.turn()).isEqualTo(PlayerColor.WHITE);
        assertThat(afterBlack.moves()).hasSize(2);
    }

    @Test
    @DisplayName("Fool's Mate ends the game as BLACK_WON")
    void foolsMateEndsGame() {
        GameDto game = newAiGame(RAJAT, "rajat", PlayerColor.WHITE);
        gameService.makeMove(game.id(), new MoveRequest("f2", "f3", null), RAJAT);
        gameService.makeMove(game.id(), new MoveRequest("e7", "e5", null), RAJAT);
        gameService.makeMove(game.id(), new MoveRequest("g2", "g4", null), RAJAT);
        GameDto ended = gameService.makeMove(game.id(), new MoveRequest("d8", "h4", null), RAJAT);

        assertThat(ended.status()).isEqualTo(GameStatus.BLACK_WON);
        assertThat(ended.result()).isEqualTo("0-1");
    }

    @Test
    @DisplayName("resigning awards the win to the opponent")
    void resignAwardsWin() {
        GameDto game = newHumanGame(RAJAT, "rajat");
        GameDto joined = gameService.joinGame(game.id(), SAM, "sam");
        GameDto after = gameService.resign(joined.id(), SAM);
        assertThat(after.status()).isEqualTo(GameStatus.WHITE_WON);
    }

    @Test
    @DisplayName("a non-player cannot resign someone else's game")
    void resignByNonPlayerRejected() {
        GameDto game = newHumanGame(RAJAT, "rajat");
        GameDto joined = gameService.joinGame(game.id(), SAM, "sam");
        assertThatThrownBy(() -> gameService.resign(joined.id(), ALEX))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("unknown game ids surface as not found")
    void unknownGameNotFound() {
        assertThatThrownBy(() -> gameService.getGame("does-not-exist"))
                .isInstanceOf(GameNotFoundException.class);
    }
}
