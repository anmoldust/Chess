package com.ccmmd.chess.gameservice.service;

import com.ccmmd.chess.gameservice.domain.ChessRules;
import com.ccmmd.chess.gameservice.domain.Game;
import com.ccmmd.chess.gameservice.domain.GameStatus;
import com.ccmmd.chess.gameservice.domain.MoveRecord;
import com.ccmmd.chess.gameservice.domain.PlayerColor;
import com.ccmmd.chess.gameservice.domain.TimeControl;
import com.ccmmd.chess.gameservice.dto.GameDto;
import com.ccmmd.chess.gameservice.dto.GameSummaryDto;
import com.ccmmd.chess.gameservice.dto.MoveDto;

import java.util.List;

public final class GameMapper {

    private GameMapper() {
    }

    public static GameSummaryDto toSummary(Game game) {
        return new GameSummaryDto(
                game.getId(),
                game.getStatus(),
                game.getMode(),
                game.getWhiteName(),
                game.getBlackName(),
                game.getWhiteUserId(),
                game.getBlackUserId(),
                resultOf(game.getStatus()),
                game.getEndReason(),
                game.getMoves().size(),
                game.getCreatedAt());
    }

    public static GameDto toDto(Game game) {
        List<MoveDto> moves = game.getMoves().stream()
                .map(GameMapper::toMoveDto)
                .toList();

        return new GameDto(
                game.getId(),
                game.getFen(),
                game.getStatus(),
                game.getMode(),
                timeControlOf(game),
                game.getWhiteName(),
                game.getBlackName(),
                game.getWhiteUserId(),
                game.getBlackUserId(),
                game.getAiColor(),
                ChessRules.turnOf(game.getFen()),
                resultOf(game.getStatus()),
                game.getEndReason(),
                game.getDrawOfferBy(),
                game.getRematchOfferBy(),
                game.getRematchGameId(),
                game.hasClock() ? game.remainingMs(PlayerColor.WHITE) : null,
                game.hasClock() ? game.remainingMs(PlayerColor.BLACK) : null,
                game.getIncrementMs(),
                moves,
                game.getVersion());
    }

    private static MoveDto toMoveDto(MoveRecord record) {
        return new MoveDto(
                record.getPly(),
                record.getFrom(),
                record.getTo(),
                record.getPromotion(),
                record.getUci(),
                record.getFenAfter()
        );
    }

    private static String resultOf(GameStatus status) {
        return switch (status) {
            case WHITE_WON -> "1-0";
            case BLACK_WON -> "0-1";
            case DRAW -> "1/2-1/2";
            default -> null;
        };
    }

    /** The control's name, recovered from its clock; null when the clock has no matching name. */
    private static String timeControlOf(Game game) {
        TimeControl control = TimeControl.of(game.getInitialMs(), game.getIncrementMs());
        return control == null ? null : control.name();
    }
}
