package com.ccmmd.chess.gameservice.dto;

import com.ccmmd.chess.gameservice.domain.GameEndReason;
import com.ccmmd.chess.gameservice.domain.GameMode;
import com.ccmmd.chess.gameservice.domain.GameStatus;

import java.time.Instant;

/**
 * A game as it appears in a list — a history row.
 *
 * <p>Deliberately not {@link GameDto}: that carries every move, which a list does not need and
 * which would make a page of history as heavy as the games themselves.
 */
public record GameSummaryDto(
        String id,
        GameStatus status,
        GameMode mode,
        String whitePlayer,
        String blackPlayer,
        String whiteUserId,
        String blackUserId,
        String result,
        GameEndReason endReason,
        int plyCount,
        Instant createdAt
) {
}
