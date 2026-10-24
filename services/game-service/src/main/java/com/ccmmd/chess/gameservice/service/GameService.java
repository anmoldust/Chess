package com.ccmmd.chess.gameservice.service;

import com.ccmmd.chess.gameservice.domain.ChessRules;
import com.ccmmd.chess.gameservice.domain.Game;
import com.ccmmd.chess.gameservice.domain.GameEndReason;
import com.ccmmd.chess.gameservice.domain.GameMode;
import com.ccmmd.chess.gameservice.domain.GameStatus;
import com.ccmmd.chess.gameservice.domain.MoveRecord;
import com.ccmmd.chess.gameservice.domain.PlayerColor;
import com.ccmmd.chess.gameservice.domain.TimeControl;
import com.ccmmd.chess.gameservice.dto.CreateGameRequest;
import com.ccmmd.chess.gameservice.dto.GameDto;
import com.ccmmd.chess.gameservice.dto.MoveRequest;
import com.ccmmd.chess.gameservice.exception.GameConflictException;
import com.ccmmd.chess.gameservice.exception.GameNotFoundException;
import com.ccmmd.chess.gameservice.exception.IllegalMoveException;
import com.ccmmd.chess.gameservice.repository.GameRepository;
import com.ccmmd.chess.platform.events.EventType;
import com.ccmmd.chess.platform.events.payload.EngineMoveComputed;
import com.ccmmd.chess.platform.events.payload.EngineMoveRequested;
import com.ccmmd.chess.platform.events.payload.GameCreated;
import com.ccmmd.chess.platform.events.payload.GameEnded;
import com.ccmmd.chess.platform.events.payload.MatchFound;
import com.ccmmd.chess.platform.events.payload.MovePlayed;
import com.ccmmd.chess.platform.messaging.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The strongly consistent core. A move is validated and applied here, in one transaction, and is
 * only *announced* afterwards via the outbox — it is never mediated by the bus.
 *
 * <p>Clocks are wall-clock: the time a move spends is measured from when the previous move landed.
 * That is deliberately server-side — a client-supplied clock would be trivial to lie about.
 */
@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private static final String PRODUCER = "game-service";
    private static final String AI_NAME = "Stockfish";
    private static final String DEFAULT_DIFFICULTY = "MEDIUM";

    private final GameRepository repository;
    private final OutboxPublisher outbox;
    private final GameEventPublisher publisher;

    public GameService(GameRepository repository, OutboxPublisher outbox, GameEventPublisher publisher) {
        this.repository = repository;
        this.outbox = outbox;
        this.publisher = publisher;
    }

    @Transactional
    public GameDto createGame(CreateGameRequest request, String userId, String displayName) {
        requireSignedIn(userId, "create a game");

        GameMode mode = request != null && request.mode() != null ? request.mode() : GameMode.HUMAN_VS_HUMAN;
        PlayerColor seat = (request != null && request.preferredColor() != null
                ? request.preferredColor()
                : PlayerColor.WHITE).resolve();
        String name = normalizeName(displayName, "Player");
        String difficulty = normalizeDifficulty(request == null ? null : request.difficulty());
        TimeControl control = TimeControl.from(request == null ? null : request.timeControl());

        GameStatus status = mode == GameMode.HUMAN_VS_AI
                ? GameStatus.IN_PROGRESS
                : GameStatus.WAITING_FOR_OPPONENT;

        Game game = new Game(UUID.randomUUID().toString(), mode, status, ChessRules.START_FEN);
        if (seat == PlayerColor.WHITE) {
            game.seatWhite(userId, name);
            if (mode == GameMode.HUMAN_VS_AI) {
                game.seatBlack(null, AI_NAME);
                game.setAiColor(PlayerColor.BLACK);
            }
        } else {
            game.seatBlack(userId, name);
            if (mode == GameMode.HUMAN_VS_AI) {
                game.seatWhite(null, AI_NAME);
                game.setAiColor(PlayerColor.WHITE);
            }
        }
        game.setDifficulty(difficulty);
        game.prepareClock(control.initialMs(), control.incrementMs());

        // A game that is playable immediately starts its clock now; a waiting one starts on join.
        if (status == GameStatus.IN_PROGRESS) {
            game.startClock(Instant.now());
        }

        Game saved = repository.saveAndFlush(game);
        publishGameCreated(saved);
        requestEngineMoveIfNeeded(saved);

        return GameMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public GameDto getGame(String id) {
        return GameMapper.toDto(require(id));
    }

    /**
     * Creates the game for a matchmaking pairing, with the id matchmaking already announced.
     *
     * <p>Idempotent on the game id, not on the event: a redelivery after a restart arrives with no
     * "already processed" record, and creating a second game under the same id would fail anyway —
     * so the presence check makes that a quiet no-op instead of a dead-letter.
     */
    @Transactional
    public Optional<GameDto> createMatchedGame(MatchFound match) {
        if (repository.existsById(match.gameId())) {
            log.debug("game {} already exists for match {}", match.gameId(), match.matchId());
            return Optional.empty();
        }

        TimeControl control = TimeControl.from(match.timeControl());

        Game game = new Game(
                match.gameId(), GameMode.HUMAN_VS_HUMAN, GameStatus.IN_PROGRESS, ChessRules.START_FEN);
        game.seatWhite(match.whiteUserId(), normalizeName(match.whiteDisplayName(), "White"));
        game.seatBlack(match.blackUserId(), normalizeName(match.blackDisplayName(), "Black"));
        game.prepareClock(control.initialMs(), control.incrementMs());
        game.startClock(Instant.now());

        Game saved = repository.saveAndFlush(game);
        publishGameCreated(saved);

        log.info("created game {} from match {}", saved.getId(), match.matchId());
        return Optional.of(GameMapper.toDto(saved));
    }

    @Transactional(readOnly = true)
    public List<GameDto> listGames(GameStatus status) {
        List<Game> games = status != null
                ? repository.findByStatusOrderByCreatedAtDesc(status)
                : repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        return games.stream().map(GameMapper::toDto).toList();
    }

    @Transactional
    public GameDto joinGame(String id, String userId, String displayName) {
        requireSignedIn(userId, "join a game");

        Game game = require(id);
        if (game.getMode() == GameMode.HUMAN_VS_AI) {
            throw new GameConflictException("This game is against the computer");
        }
        if (game.getStatus() != GameStatus.WAITING_FOR_OPPONENT) {
            throw new GameConflictException("Game " + id + " is not open to join");
        }
        if (game.colorOf(userId) != null) {
            throw new GameConflictException("You are already in this game");
        }

        String name = normalizeName(displayName, "Opponent");
        if (game.getWhiteUserId() == null) {
            game.seatWhite(userId, name);
        } else if (game.getBlackUserId() == null) {
            game.seatBlack(userId, name);
        } else {
            throw new GameConflictException("Game " + id + " is already full");
        }

        game.setStatus(GameStatus.IN_PROGRESS);
        game.startClock(Instant.now());
        return GameMapper.toDto(repository.saveAndFlush(game));
    }

    @Transactional
    public GameDto makeMove(String id, MoveRequest request, String userId) {
        requireSignedIn(userId, "play a move");

        Game game = require(id);
        if (game.getStatus() == GameStatus.WAITING_FOR_OPPONENT) {
            throw new GameConflictException("Game " + id + " has not started yet");
        }
        if (game.getStatus().isOver()) {
            throw new GameConflictException("Game " + id + " is already over");
        }
        authorizeMove(game, userId);

        return applyMove(game, request.from(), request.to(), request.promotion(), userId);
    }

    /**
     * Applies a move the engine computed. Returns empty when the reply is not for the current
     * position — a duplicate delivery, or an answer to a position that has since moved on — which
     * is what makes redelivery safe without any extra bookkeeping.
     */
    @Transactional
    public Optional<GameDto> applyEngineMove(EngineMoveComputed reply) {
        if (reply.failed() || reply.uci() == null) {
            log.warn("engine could not move for game {}: {}", reply.gameId(), reply.failureReason());
            return Optional.empty();
        }

        Game game = repository.findById(reply.gameId()).orElse(null);
        if (game == null) {
            log.warn("engine answered for unknown game {}", reply.gameId());
            return Optional.empty();
        }
        if (game.getMode() != GameMode.HUMAN_VS_AI || game.getAiColor() == null) {
            return Optional.empty();
        }
        if (!game.getStatus().equals(GameStatus.IN_PROGRESS)) {
            return Optional.empty();
        }
        PlayerColor turn = ChessRules.turnOf(game.getFen());
        if (turn != game.getAiColor()) {
            return Optional.empty();
        }

        int currentPly = game.getMoves().size() + 1;
        if (!correlationFor(game.getId(), currentPly).equals(reply.correlationId())) {
            log.debug("ignoring stale engine reply {} (current ply {})", reply.correlationId(), currentPly);
            return Optional.empty();
        }

        String uci = reply.uci();
        String from = uci.substring(0, 2);
        String to = uci.substring(2, 4);
        String promotion = uci.length() > 4 ? uci.substring(4, 5) : null;

        try {
            return Optional.of(applyMove(game, from, to, promotion, null));
        } catch (IllegalMoveException illegal) {
            log.warn("engine returned {} which is not legal in game {}", uci, game.getId());
            return Optional.empty();
        }
    }

    @Transactional
    public GameDto resign(String id, String userId) {
        requireSignedIn(userId, "resign");

        Game game = require(id);
        PlayerColor seat = game.colorOf(userId);
        if (seat == null) {
            throw new AccessDeniedException("You are not a player in this game");
        }
        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new GameConflictException("Game " + id + " is not in progress");
        }

        game.setStatus(seat == PlayerColor.WHITE ? GameStatus.BLACK_WON : GameStatus.WHITE_WON);
        game.setEndReason(GameEndReason.RESIGNATION);
        game.stopClock();
        Game saved = repository.saveAndFlush(game);
        publishGameEnded(saved);

        return GameMapper.toDto(saved);
    }

    /**
     * Ends games whose clock has run out while nobody was moving.
     *
     * <p>Server-side on purpose: the client cannot be trusted to report its own flag fall, and a
     * disconnected player must still lose on time.
     */
    @Scheduled(
            fixedDelayString = "${ccmmd.game.timeout-sweep-ms:5000}",
            initialDelayString = "${ccmmd.game.timeout-sweep-initial-ms:15000}")
    @Transactional
    public void sweepTimeouts() {
        Instant now = Instant.now();

        for (Game game : repository.findByStatus(GameStatus.IN_PROGRESS)) {
            if (!game.hasClock() || game.getClockStartedAt() == null) {
                continue;
            }
            PlayerColor toMove = ChessRules.turnOf(game.getFen());
            long elapsed = Math.max(0L, Duration.between(game.getClockStartedAt(), now).toMillis());
            if (game.remainingMs(toMove) - elapsed > 0) {
                continue;
            }
            log.info("game {} lost on time ({} flagged)", game.getId(), toMove);
            publisher.gameUpdated(applyTimeout(game, toMove));
        }
    }

    // ------------------------------------------------------------------------------------------
    // move application
    // ------------------------------------------------------------------------------------------

    private GameDto applyMove(Game game, String from, String to, String promotion, String byUserId) {
        PlayerColor mover = ChessRules.turnOf(game.getFen());
        Instant now = Instant.now();

        long remainingMs = 0L;
        if (game.hasClock()) {
            long elapsed = game.getClockStartedAt() == null
                    ? 0L
                    : Math.max(0L, Duration.between(game.getClockStartedAt(), now).toMillis());
            remainingMs = game.remainingMs(mover) - elapsed;

            // Tried to move with no time left: the game is lost on time, and no move is applied.
            if (remainingMs <= 0) {
                return applyTimeout(game, mover);
            }
        }

        ChessRules.AppliedMove applied = ChessRules.apply(game.getFen(), from, to, promotion);
        if (applied == null) {
            throw new IllegalMoveException(uciOf(from, to, promotion) + " is not a legal move");
        }

        int ply = game.getMoves().size() + 1;
        game.appendMove(new MoveRecord(
                ply,
                from.toLowerCase(Locale.ROOT),
                to.toLowerCase(Locale.ROOT),
                normalizePromotion(promotion),
                applied.uci(),
                applied.fenAfter()));
        game.setFen(applied.fenAfter());

        GameStatus status = applied.status();
        GameEndReason reason = applied.reason();

        // chesslib cannot see repetition from a bare FEN; this service owns the history.
        if (status == GameStatus.IN_PROGRESS && isThreefoldRepetition(game)) {
            status = GameStatus.DRAW;
            reason = GameEndReason.THREEFOLD_REPETITION;
        }

        game.setStatus(status);
        game.setEndReason(reason);

        if (status.isOver()) {
            game.stopClock();
        } else if (game.hasClock()) {
            game.chargeClock(mover, remainingMs, now);
        }

        Game saved = repository.saveAndFlush(game);

        outbox.publish(
                EventType.GAME_MOVE_PLAYED,
                PRODUCER,
                saved.getId(),
                null,
                new MovePlayed(saved.getId(), ply, applied.uci(), applied.fenAfter(),
                        saved.getStatus().name(), byUserId));

        if (saved.getStatus().isOver()) {
            publishGameEnded(saved);
        } else {
            // If that move handed the turn to the computer, ask the engine for its reply.
            requestEngineMoveIfNeeded(saved);
        }

        return GameMapper.toDto(saved);
    }

    private GameDto applyTimeout(Game game, PlayerColor flagged) {
        game.setStatus(flagged == PlayerColor.WHITE ? GameStatus.BLACK_WON : GameStatus.WHITE_WON);
        game.setEndReason(GameEndReason.TIMEOUT);
        game.stopClock();

        Game saved = repository.saveAndFlush(game);
        publishGameEnded(saved);
        return GameMapper.toDto(saved);
    }

    /**
     * Threefold repetition, counted over the positions the game has actually held.
     *
     * <p>Compared on the first four FEN fields only — board, side, castling rights, en-passant
     * target. The halfmove and fullmove counters differ between otherwise identical positions, and
     * including them would mean repetition never matched.
     */
    private static boolean isThreefoldRepetition(Game game) {
        String key = repetitionKey(game.getFen());

        long occurrences = game.getMoves().stream()
                .filter(move -> key.equals(repetitionKey(move.getFenAfter())))
                .count();

        if (repetitionKey(ChessRules.START_FEN).equals(key)) {
            occurrences++;
        }
        return occurrences >= 3;
    }

    static String repetitionKey(String fen) {
        String[] fields = fen.split(" ");
        if (fields.length < 4) {
            return fen;
        }
        return fields[0] + " " + fields[1] + " " + fields[2] + " " + fields[3];
    }

    // ------------------------------------------------------------------------------------------
    // events
    // ------------------------------------------------------------------------------------------

    private void publishGameCreated(Game game) {
        outbox.publish(
                EventType.GAME_CREATED,
                PRODUCER,
                game.getId(),
                null,
                new GameCreated(
                        game.getId(),
                        game.getMode().name(),
                        game.getWhiteUserId(),
                        game.getBlackUserId(),
                        game.getAiColor() == null ? null : game.getAiColor().name(),
                        game.getCreatedAt()));
    }

    private void publishGameEnded(Game game) {
        outbox.publish(
                EventType.GAME_ENDED,
                PRODUCER,
                game.getId(),
                null,
                new GameEnded(
                        game.getId(),
                        game.getStatus().name(),
                        GameMapper.toDto(game).result(),
                        game.getMoves().size(),
                        game.getWhiteUserId(),
                        game.getBlackUserId()));
    }

    /**
     * Asks engine-service for a move, from inside the transaction that made it the computer's turn.
     * The correlation id encodes the ply, so a late or duplicated answer can be recognised and
     * ignored rather than applied to the wrong position.
     */
    private void requestEngineMoveIfNeeded(Game game) {
        if (game.getMode() != GameMode.HUMAN_VS_AI || game.getAiColor() == null) {
            return;
        }
        if (!game.getStatus().equals(GameStatus.IN_PROGRESS)) {
            return;
        }
        if (ChessRules.turnOf(game.getFen()) != game.getAiColor()) {
            return;
        }

        String correlationId = correlationFor(game.getId(), game.getMoves().size() + 1);
        outbox.publish(
                EventType.ENGINE_MOVE_REQUESTED,
                PRODUCER,
                correlationId,
                null,
                new EngineMoveRequested(game.getId(), game.getFen(), game.getDifficulty(), correlationId));
    }

    private static String correlationFor(String gameId, int ply) {
        return gameId + ":" + ply;
    }

    /** Only the account seated on the side to move may move — including the computer's seat. */
    private static void authorizeMove(Game game, String userId) {
        PlayerColor turn = ChessRules.turnOf(game.getFen());
        String seatUserId = game.playerUserId(turn);
        if (!userId.equals(seatUserId)) {
            throw new AccessDeniedException("It is not your turn");
        }
    }

    private static void requireSignedIn(String userId, String action) {
        if (userId == null) {
            throw new AccessDeniedException("Sign in to " + action);
        }
    }

    private Game require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new GameNotFoundException("Game " + id + " was not found"));
    }

    private static String normalizeName(String name, String fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        String trimmed = name.trim();
        return trimmed.length() > 60 ? trimmed.substring(0, 60) : trimmed;
    }

    private static String normalizeDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return DEFAULT_DIFFICULTY;
        }
        String trimmed = difficulty.trim().toUpperCase(Locale.ROOT);
        return trimmed.length() > 16 ? DEFAULT_DIFFICULTY : trimmed;
    }

    private static String normalizePromotion(String promotion) {
        return promotion == null || promotion.isBlank() ? null : promotion.trim().toUpperCase(Locale.ROOT);
    }

    private static String uciOf(String from, String to, String promotion) {
        String promo = promotion == null ? "" : promotion.toLowerCase(Locale.ROOT);
        return from.toLowerCase(Locale.ROOT) + to.toLowerCase(Locale.ROOT) + promo;
    }
}
