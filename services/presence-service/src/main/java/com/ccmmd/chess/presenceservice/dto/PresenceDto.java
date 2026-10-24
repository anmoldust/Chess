package com.ccmmd.chess.presenceservice.dto;

/** {@code gameId} is non-null only while the player is mid-game. */
public record PresenceDto(String userId, boolean online, String gameId) {
}
