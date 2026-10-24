package com.ccmmd.chess.service;

import com.ccmmd.chess.domain.Game;
import com.ccmmd.chess.domain.GameMode;
import com.ccmmd.chess.domain.GameStatus;
import com.ccmmd.chess.domain.MoveRecord;
import com.ccmmd.chess.domain.PlayerColor;
import com.ccmmd.chess.dto.CreateGameRequest;
import com.ccmmd.chess.dto.GameDto;
import com.ccmmd.chess.dto.MoveRequest;
import com.ccmmd.chess.exception.GameConflictException;
import com.ccmmd.chess.exception.GameNotFoundException;
import com.ccmmd.chess.exception.IllegalMoveException;
import com.ccmmd.chess.repository.GameRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class GameService {

    private static final String AI_NAME = "Stockfish";

    private final GameRepository repository;

    public GameService(GameRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public GameDto createGame(CreateGameRequest request, String userId, String displayName) {
        requireSignedIn(userId, "create a game");

        GameMode mode = request != null && request.mode() != null ? request.mode() : GameMode.HUMAN_VS_HUMAN;
        PlayerColor seat = (request != null && request.preferredColor() != null
                ? request.preferredColor()
                : PlayerColor.WHITE).resolve();
        String name = normalizeName(displayName, "Player");

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

        return GameMapper.toDto(repository.saveAndFlush(game));
    }

    @Transactional(readOnly = true)
    public GameDto getGame(String id) {
        return GameMapper.toDto(require(id));
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

        ChessRules.AppliedMove applied =
                ChessRules.apply(game.getFen(), request.from(), request.to(), request.promotion());
        if (applied == null) {
            throw new IllegalMoveException(uciOf(request) + " is not a legal move");
        }

        int ply = game.getMoves().size() + 1;
        game.appendMove(new MoveRecord(
                ply,
                request.from().toLowerCase(Locale.ROOT),
                request.to().toLowerCase(Locale.ROOT),
                normalizePromotion(request.promotion()),
                applied.uci(),
                applied.fenAfter()));
        game.setFen(applied.fenAfter());
        game.setStatus(applied.status());

        return GameMapper.toDto(repository.saveAndFlush(game));
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
        return GameMapper.toDto(repository.saveAndFlush(game));
    }

    /**
     * Only the account seated on the side to move may move — except in a game against the
     * computer, where the human's client drives the engine's replies, so the owner may move
     * for both seats.
     */
    private static void authorizeMove(Game game, String userId) {
        if (game.getMode() == GameMode.HUMAN_VS_AI) {
            if (!userId.equals(game.humanUserId())) {
                throw new AccessDeniedException("This is not your game");
            }
            return;
        }

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

    private static String normalizePromotion(String promotion) {
        return promotion == null || promotion.isBlank() ? null : promotion.trim().toUpperCase(Locale.ROOT);
    }

    private static String uciOf(MoveRequest request) {
        String promo = request.promotion() == null ? "" : request.promotion().toLowerCase(Locale.ROOT);
        return request.from().toLowerCase(Locale.ROOT) + request.to().toLowerCase(Locale.ROOT) + promo;
    }
}
