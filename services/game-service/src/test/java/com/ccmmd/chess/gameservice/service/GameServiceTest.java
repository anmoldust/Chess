package com.ccmmd.chess.gameservice.service;

import com.ccmmd.chess.gameservice.domain.ChessRules;
import com.ccmmd.chess.gameservice.domain.GameEndReason;
import com.ccmmd.chess.gameservice.domain.GameMode;
import com.ccmmd.chess.gameservice.domain.GameStatus;
import com.ccmmd.chess.gameservice.domain.PlayerColor;
import com.ccmmd.chess.gameservice.dto.CreateGameRequest;
import com.ccmmd.chess.gameservice.dto.GameDto;
import com.ccmmd.chess.gameservice.dto.MoveRequest;
import com.ccmmd.chess.gameservice.exception.GameConflictException;
import com.ccmmd.chess.gameservice.exception.GameNotFoundException;
import com.ccmmd.chess.gameservice.exception.IllegalMoveException;
import com.ccmmd.chess.platform.events.EventType;
import com.ccmmd.chess.platform.events.payload.EngineMoveComputed;
import com.ccmmd.chess.platform.messaging.OutboxEntry;
import com.ccmmd.chess.platform.messaging.OutboxRepository;
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

    @Autowired
    private OutboxRepository outbox;

    private GameDto newHumanGame(String userId, String name) {
        return gameService.createGame(
                new CreateGameRequest(GameMode.HUMAN_VS_HUMAN, PlayerColor.WHITE, null, "BLITZ"), userId, name);
    }

    private GameDto newAiGame(String userId, String name, PlayerColor color) {
        return gameService.createGame(
                new CreateGameRequest(GameMode.HUMAN_VS_AI, color, "HARD", "BLITZ"), userId, name);
    }

    private static String correlation(String gameId, int ply) {
        return gameId + ":" + ply;
    }

    /**
     * Events written for one game only. The outbox outlives an individual test — asserting on it
     * globally picks up rows other tests left behind, which is a false result either way.
     */
    private java.util.List<String> eventsFor(String gameId) {
        return outbox.findAll().stream()
                .filter(entry -> entry.getEnvelopeJson().contains(gameId))
                .map(OutboxEntry::getType)
                .toList();
    }

    private static EngineMoveComputed engineSays(String gameId, int ply, String uci) {
        return new EngineMoveComputed(correlation(gameId, ply), gameId, uci, null, false, null);
    }

    private void playEngineReply(GameDto game, int ply, String uci) {
        assertThat(gameService.applyEngineMove(engineSays(game.id(), ply, uci))).isPresent();
    }

    @Test
    @DisplayName("a human-vs-human game starts waiting, owned by the creator")
    void createHumanGame() {
        GameDto game = newHumanGame(RAJAT, "rajat");
        assertThat(game.status()).isEqualTo(GameStatus.WAITING_FOR_OPPONENT);
        assertThat(game.whitePlayer()).isEqualTo("rajat");
        assertThat(game.whiteUserId()).isEqualTo(RAJAT);
        assertThat(game.fen()).isEqualTo(ChessRules.START_FEN);
    }

    @Test
    @DisplayName("creating a game queues a game.created event")
    void createPublishesGameCreated() {
        GameDto game = newHumanGame(RAJAT, "rajat");
        assertThat(eventsFor(game.id())).contains(EventType.GAME_CREATED);
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
    @DisplayName("an AI game where the computer moves first asks the engine straight away")
    void aiMovingFirstRequestsAnEngineMoveImmediately() {
        GameDto game = newAiGame(RAJAT, "rajat", PlayerColor.BLACK);
        assertThat(eventsFor(game.id())).contains(EventType.ENGINE_MOVE_REQUESTED);
    }

    @Test
    @DisplayName("an anonymous caller cannot create a game")
    void createRequiresAuth() {
        assertThatThrownBy(() -> gameService.createGame(
                new CreateGameRequest(GameMode.HUMAN_VS_HUMAN, PlayerColor.WHITE, null, null), null, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("an opponent can take the open seat")
    void joinGame() {
        GameDto joined = gameService.joinGame(newHumanGame(RAJAT, "rajat").id(), SAM, "sam");
        assertThat(joined.status()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(joined.blackPlayer()).isEqualTo("sam");
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
    @DisplayName("a legal move advances the board and queues move.played")
    void legalMoveAdvancesGame() {
        GameDto game = newAiGame(RAJAT, "rajat", PlayerColor.WHITE);
        GameDto after = gameService.makeMove(game.id(), new MoveRequest("e2", "e4", null), RAJAT);

        assertThat(after.moves()).hasSize(1);
        assertThat(after.moves().get(0).uci()).isEqualTo("e2e4");
        assertThat(after.turn()).isEqualTo(PlayerColor.BLACK);
        assertThat(eventsFor(game.id())).contains(EventType.GAME_MOVE_PLAYED);
    }

    @Test
    @DisplayName("a human move that hands the turn to the computer queues an engine request")
    void humanMoveRequestsEngineReply() {
        GameDto game = newAiGame(RAJAT, "rajat", PlayerColor.WHITE);
        gameService.makeMove(game.id(), new MoveRequest("e2", "e4", null), RAJAT);

        assertThat(eventsFor(game.id())).contains(EventType.ENGINE_MOVE_REQUESTED);
    }

    @Test
    @DisplayName("the owner can NO LONGER move for the computer")
    void ownerCannotMoveForTheComputer() {
        GameDto game = newAiGame(RAJAT, "rajat", PlayerColor.WHITE);
        gameService.makeMove(game.id(), new MoveRequest("e2", "e4", null), RAJAT);

        // This is the hole that stage 3 closes: the AI's seat is not a seat anyone may drive.
        assertThatThrownBy(() -> gameService.makeMove(game.id(), new MoveRequest("e7", "e5", null), RAJAT))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("the engine's reply is applied through the same validated path")
    void engineReplyIsApplied() {
        GameDto game = newAiGame(RAJAT, "rajat", PlayerColor.WHITE);
        gameService.makeMove(game.id(), new MoveRequest("e2", "e4", null), RAJAT);

        playEngineReply(game, 2, "e7e5");

        GameDto after = gameService.getGame(game.id());
        assertThat(after.moves()).hasSize(2);
        assertThat(after.moves().get(1).uci()).isEqualTo("e7e5");
        assertThat(after.turn()).isEqualTo(PlayerColor.WHITE);
    }

    @Test
    @DisplayName("an engine reply for a position that has moved on is ignored")
    void staleEngineReplyIsIgnored() {
        GameDto game = newAiGame(RAJAT, "rajat", PlayerColor.WHITE);
        gameService.makeMove(game.id(), new MoveRequest("e2", "e4", null), RAJAT);

        // Same move, but labelled as the answer to ply 4 rather than ply 2.
        assertThat(gameService.applyEngineMove(engineSays(game.id(), 4, "e7e5"))).isEmpty();
        assertThat(gameService.getGame(game.id()).moves()).hasSize(1);
    }

    @Test
    @DisplayName("a duplicated engine reply is applied only once")
    void duplicateEngineReplyIsIgnored() {
        GameDto game = newAiGame(RAJAT, "rajat", PlayerColor.WHITE);
        gameService.makeMove(game.id(), new MoveRequest("e2", "e4", null), RAJAT);

        assertThat(gameService.applyEngineMove(engineSays(game.id(), 2, "e7e5"))).isPresent();
        assertThat(gameService.applyEngineMove(engineSays(game.id(), 2, "e7e5"))).isEmpty();
        assertThat(gameService.getGame(game.id()).moves()).hasSize(2);
    }

    @Test
    @DisplayName("a failed engine reply leaves the game untouched")
    void failedEngineReplyIsIgnored() {
        GameDto game = newAiGame(RAJAT, "rajat", PlayerColor.WHITE);
        gameService.makeMove(game.id(), new MoveRequest("e2", "e4", null), RAJAT);

        EngineMoveComputed failed = new EngineMoveComputed(
                correlation(game.id(), 2), game.id(), null, null, true, "engine crashed");

        assertThat(gameService.applyEngineMove(failed)).isEmpty();
        assertThat(gameService.getGame(game.id()).moves()).hasSize(1);
    }

    @Test
    @DisplayName("an illegal engine move is refused rather than trusted")
    void illegalEngineReplyIsRefused() {
        GameDto game = newAiGame(RAJAT, "rajat", PlayerColor.WHITE);
        gameService.makeMove(game.id(), new MoveRequest("e2", "e4", null), RAJAT);

        assertThat(gameService.applyEngineMove(engineSays(game.id(), 2, "e7e4"))).isEmpty();
        assertThat(gameService.getGame(game.id()).moves()).hasSize(1);
    }

    @Test
    @DisplayName("an illegal human move is rejected and leaves the board untouched")
    void illegalMoveRejected() {
        GameDto game = newAiGame(RAJAT, "rajat", PlayerColor.WHITE);
        assertThatThrownBy(() -> gameService.makeMove(game.id(), new MoveRequest("e2", "e5", null), RAJAT))
                .isInstanceOf(IllegalMoveException.class);
        assertThat(gameService.getGame(game.id()).moves()).isEmpty();
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
        GameDto game = gameService.joinGame(newHumanGame(RAJAT, "rajat").id(), SAM, "sam");
        assertThatThrownBy(() -> gameService.makeMove(game.id(), new MoveRequest("e2", "e4", null), SAM))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Fool's Mate ends the game and queues game.ended")
    void foolsMateEndsGame() {
        GameDto game = gameService.joinGame(newHumanGame(RAJAT, "rajat").id(), SAM, "sam");

        gameService.makeMove(game.id(), new MoveRequest("f2", "f3", null), RAJAT);
        gameService.makeMove(game.id(), new MoveRequest("e7", "e5", null), SAM);
        gameService.makeMove(game.id(), new MoveRequest("g2", "g4", null), RAJAT);
        GameDto ended = gameService.makeMove(game.id(), new MoveRequest("d8", "h4", null), SAM);

        assertThat(ended.status()).isEqualTo(GameStatus.BLACK_WON);
        assertThat(ended.result()).isEqualTo("0-1");
        assertThat(eventsFor(game.id())).contains(EventType.GAME_ENDED);
    }

    @Test
    @DisplayName("no engine is asked for a move once the game is over")
    void noEngineRequestAfterGameEnds() {
        GameDto game = gameService.joinGame(newHumanGame(RAJAT, "rajat").id(), SAM, "sam");
        gameService.makeMove(game.id(), new MoveRequest("f2", "f3", null), RAJAT);
        gameService.makeMove(game.id(), new MoveRequest("e7", "e5", null), SAM);
        gameService.makeMove(game.id(), new MoveRequest("g2", "g4", null), RAJAT);
        gameService.makeMove(game.id(), new MoveRequest("d8", "h4", null), SAM);

        assertThat(eventsFor(game.id())).doesNotContain(EventType.ENGINE_MOVE_REQUESTED);
    }

    @Test
    @DisplayName("resigning awards the win to the opponent")
    void resignAwardsWin() {
        GameDto game = gameService.joinGame(newHumanGame(RAJAT, "rajat").id(), SAM, "sam");
        assertThat(gameService.resign(game.id(), SAM).status()).isEqualTo(GameStatus.WHITE_WON);
    }

    @Test
    @DisplayName("a non-player cannot resign someone else's game")
    void resignByNonPlayerRejected() {
        GameDto game = gameService.joinGame(newHumanGame(RAJAT, "rajat").id(), SAM, "sam");
        assertThatThrownBy(() -> gameService.resign(game.id(), ALEX))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("unknown game ids surface as not found")
    void unknownGameNotFound() {
        assertThatThrownBy(() -> gameService.getGame("does-not-exist"))
                .isInstanceOf(GameNotFoundException.class);
    }

    private static com.ccmmd.chess.platform.events.payload.MatchFound aMatch(String gameId) {
        return new com.ccmmd.chess.platform.events.payload.MatchFound(
                "match-" + gameId,
                gameId,
                "user-white",
                "user-black",
                "White Player",
                "Black Player",
                "BLITZ",
                java.time.Instant.now());
    }

    @Test
    @DisplayName("a matchmaking pairing becomes a game under the announced id")
    void matchBecomesGame() {
        String gameId = java.util.UUID.randomUUID().toString();

        assertThat(gameService.createMatchedGame(aMatch(gameId))).isPresent();

        GameDto game = gameService.getGame(gameId);
        assertThat(game.whiteUserId()).isEqualTo("user-white");
        assertThat(game.blackUserId()).isEqualTo("user-black");
        assertThat(game.whitePlayer()).isEqualTo("White Player");
        assertThat(game.blackPlayer()).isEqualTo("Black Player");
        assertThat(game.mode()).isEqualTo(GameMode.HUMAN_VS_HUMAN);
        assertThat(game.status()).isEqualTo(GameStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("a redelivered pairing does not create a second game")
    void duplicateMatchIsIgnored() {
        String gameId = java.util.UUID.randomUUID().toString();

        assertThat(gameService.createMatchedGame(aMatch(gameId))).isPresent();
        assertThat(gameService.createMatchedGame(aMatch(gameId))).isEmpty();

        // The id is the uniqueness guarantee, not the event.
        assertThat(gameService.getGame(gameId).moves()).isEmpty();
    }

    @Test
    @DisplayName("a position repeated three times is a draw")
    void threefoldRepetitionIsADraw() {
        GameDto game = gameService.joinGame(newHumanGame(RAJAT, "rajat").id(), SAM, "sam");

        // Knights out and back, twice: the start position occurs on plies 0, 4 and 8.
        String[][] moves = {
                {"g1", "f3"}, {"g8", "f6"}, {"f3", "g1"}, {"f6", "g8"},
                {"g1", "f3"}, {"g8", "f6"}, {"f3", "g1"}, {"f6", "g8"},
        };
        GameDto current = game;
        for (int i = 0; i < moves.length; i++) {
            String mover = i % 2 == 0 ? RAJAT : SAM;
            current = gameService.makeMove(game.id(), new MoveRequest(moves[i][0], moves[i][1], null), mover);
        }

        assertThat(current.status()).isEqualTo(GameStatus.DRAW);
        assertThat(current.endReason()).isEqualTo(GameEndReason.THREEFOLD_REPETITION);
        assertThat(current.result()).isEqualTo("1/2-1/2");
    }

    @Test
    @DisplayName("a position repeated only twice is not a draw")
    void twofoldRepetitionIsNotADraw() {
        GameDto game = gameService.joinGame(newHumanGame(RAJAT, "rajat").id(), SAM, "sam");

        GameDto current = game;
        current = gameService.makeMove(game.id(), new MoveRequest("g1", "f3", null), RAJAT);
        current = gameService.makeMove(game.id(), new MoveRequest("g8", "f6", null), SAM);
        current = gameService.makeMove(game.id(), new MoveRequest("f3", "g1", null), RAJAT);
        current = gameService.makeMove(game.id(), new MoveRequest("f6", "g8", null), SAM);

        assertThat(current.status()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(current.endReason()).isNull();
    }

    @Test
    @DisplayName("a game starts with the clock its time control specifies")
    void clockStartsFromTheTimeControl() {
        GameDto game = newAiGame(RAJAT, "rajat", PlayerColor.WHITE);

        // BLITZ is 5 minutes plus a 2-second increment.
        assertThat(game.whiteTimeMs()).isEqualTo(300_000L);
        assertThat(game.blackTimeMs()).isEqualTo(300_000L);
        assertThat(game.incrementMs()).isEqualTo(2_000L);
    }

    @Test
    @DisplayName("a waiting game does not run its clock until an opponent joins")
    void waitingGameClockStartsOnJoin() {
        GameDto waiting = newHumanGame(RAJAT, "rajat");
        assertThat(waiting.whiteTimeMs()).isEqualTo(300_000L);

        GameDto joined = gameService.joinGame(waiting.id(), SAM, "sam");
        assertThat(joined.status()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(joined.blackTimeMs()).isEqualTo(300_000L);
    }

    @Test
    @DisplayName("resigning records why the game ended")
    void resignRecordsTheReason() {
        GameDto game = gameService.joinGame(newHumanGame(RAJAT, "rajat").id(), SAM, "sam");

        GameDto ended = gameService.resign(game.id(), SAM);

        assertThat(ended.endReason()).isEqualTo(GameEndReason.RESIGNATION);
    }

    @Test
    @DisplayName("checkmate records why the game ended")
    void checkmateRecordsTheReason() {
        GameDto game = gameService.joinGame(newHumanGame(RAJAT, "rajat").id(), SAM, "sam");
        gameService.makeMove(game.id(), new MoveRequest("f2", "f3", null), RAJAT);
        gameService.makeMove(game.id(), new MoveRequest("e7", "e5", null), SAM);
        gameService.makeMove(game.id(), new MoveRequest("g2", "g4", null), RAJAT);

        GameDto ended = gameService.makeMove(game.id(), new MoveRequest("d8", "h4", null), SAM);

        assertThat(ended.endReason()).isEqualTo(GameEndReason.CHECKMATE);
    }

    @Test
    @DisplayName("a draw offer is visible to both players before anyone accepts")
    void drawOfferIsVisible() {
        GameDto game = gameService.joinGame(newHumanGame(RAJAT, "rajat").id(), SAM, "sam");

        GameDto offered = gameService.offerDraw(game.id(), RAJAT);

        assertThat(offered.drawOfferBy()).isEqualTo(PlayerColor.WHITE);
        assertThat(offered.status()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(gameService.getGame(game.id()).drawOfferBy()).isEqualTo(PlayerColor.WHITE);
    }

    @Test
    @DisplayName("offering back accepts the draw")
    void offeringBackAccepts() {
        GameDto game = gameService.joinGame(newHumanGame(RAJAT, "rajat").id(), SAM, "sam");
        gameService.offerDraw(game.id(), RAJAT);

        GameDto accepted = gameService.offerDraw(game.id(), SAM);

        assertThat(accepted.status()).isEqualTo(GameStatus.DRAW);
        assertThat(accepted.result()).isEqualTo("1/2-1/2");
        assertThat(accepted.endReason()).isEqualTo(GameEndReason.DRAW_AGREED);
        assertThat(accepted.drawOfferBy()).isNull();
    }

    @Test
    @DisplayName("offering twice does not accept your own offer")
    void offeringTwiceIsIdempotent() {
        GameDto game = gameService.joinGame(newHumanGame(RAJAT, "rajat").id(), SAM, "sam");
        gameService.offerDraw(game.id(), RAJAT);

        GameDto again = gameService.offerDraw(game.id(), RAJAT);

        assertThat(again.status()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(again.drawOfferBy()).isEqualTo(PlayerColor.WHITE);
    }

    @Test
    @DisplayName("declining clears the offer and play continues")
    void decliningKeepsPlaying() {
        GameDto game = gameService.joinGame(newHumanGame(RAJAT, "rajat").id(), SAM, "sam");
        gameService.offerDraw(game.id(), RAJAT);

        GameDto declined = gameService.declineDraw(game.id(), SAM);

        assertThat(declined.drawOfferBy()).isNull();
        assertThat(declined.status()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(gameService.makeMove(game.id(), new MoveRequest("e2", "e4", null), RAJAT).moves()).hasSize(1);
    }

    @Test
    @DisplayName("a spectator cannot offer a draw")
    void spectatorCannotOfferADraw() {
        GameDto game = gameService.joinGame(newHumanGame(RAJAT, "rajat").id(), SAM, "sam");

        assertThatThrownBy(() -> gameService.offerDraw(game.id(), ALEX))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("no side may offer a draw once the game is over")
    void cannotOfferADrawAfterTheGameEnds() {
        GameDto game = gameService.joinGame(newHumanGame(RAJAT, "rajat").id(), SAM, "sam");
        gameService.resign(game.id(), SAM);

        assertThatThrownBy(() -> gameService.offerDraw(game.id(), RAJAT))
                .isInstanceOf(GameConflictException.class);
    }

    @Test
    @DisplayName("a rematch starts only once both players ask, with the colours swapped")
    void rematchStartsOnceBothAsk() {
        GameDto game = gameService.joinGame(newHumanGame(RAJAT, "rajat").id(), SAM, "sam");
        gameService.resign(game.id(), SAM); // white (rajat) wins

        GameDto offer = gameService.rematch(game.id(), RAJAT);
        assertThat(offer.rematchOfferBy()).isEqualTo(PlayerColor.WHITE);
        assertThat(offer.rematchGameId()).isNull();

        GameDto agreed = gameService.rematch(game.id(), SAM);
        assertThat(agreed.rematchGameId()).isNotBlank();

        GameDto next = gameService.getGame(agreed.rematchGameId());
        assertThat(next.status()).isEqualTo(GameStatus.IN_PROGRESS);
        // Swapped: sam was black and now has white.
        assertThat(next.whiteUserId()).isEqualTo(SAM);
        assertThat(next.blackUserId()).isEqualTo(RAJAT);
    }

    @Test
    @DisplayName("asking twice does not start a rematch on your own")
    void askingTwiceDoesNotStartARematch() {
        GameDto game = gameService.joinGame(newHumanGame(RAJAT, "rajat").id(), SAM, "sam");
        gameService.resign(game.id(), SAM);
        gameService.rematch(game.id(), RAJAT);

        GameDto again = gameService.rematch(game.id(), RAJAT);

        assertThat(again.rematchOfferBy()).isEqualTo(PlayerColor.WHITE);
        assertThat(again.rematchGameId()).isNull();
    }

    @Test
    @DisplayName("a game still in progress cannot be rematched")
    void cannotRematchMidGame() {
        GameDto game = gameService.joinGame(newHumanGame(RAJAT, "rajat").id(), SAM, "sam");

        assertThatThrownBy(() -> gameService.rematch(game.id(), RAJAT))
                .isInstanceOf(GameConflictException.class);
    }

    @Test
    @DisplayName("a game against the computer cannot be rematched")
    void cannotRematchAiGame() {
        GameDto game = newAiGame(RAJAT, "rajat", PlayerColor.WHITE);

        assertThatThrownBy(() -> gameService.rematch(game.id(), RAJAT))
                .isInstanceOf(GameConflictException.class);
    }

    @Test
    @DisplayName("a game is created with the time control it was asked for")
    void createHonoursTimeControl() {
        GameDto rapid = gameService.createGame(
                new CreateGameRequest(GameMode.HUMAN_VS_HUMAN, PlayerColor.WHITE, null, "RAPID"),
                RAJAT, "rajat");

        assertThat(rapid.timeControl()).isEqualTo("RAPID");
        assertThat(rapid.whiteTimeMs()).isEqualTo(900_000L);
        assertThat(rapid.incrementMs()).isEqualTo(10_000L);
    }
}
