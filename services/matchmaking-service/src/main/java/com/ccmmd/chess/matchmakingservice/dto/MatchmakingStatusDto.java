package com.ccmmd.chess.matchmakingservice.dto;

/** What a waiting client polls: am I queued, and if I was matched, where do I play? */
public record MatchmakingStatusDto(boolean queued, boolean matched, String gameId) {
}
