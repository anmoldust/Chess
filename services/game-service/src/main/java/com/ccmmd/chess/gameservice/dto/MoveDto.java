package com.ccmmd.chess.gameservice.dto;

public record MoveDto(
        int ply,
        String from,
        String to,
        String promotion,
        String uci,
        String fenAfter
) {
}
