package com.ccmmd.chess.matchmakingservice.dto;

import java.time.Instant;

public record QueueStatusDto(boolean queued, int position, Instant queuedAt) {
}
