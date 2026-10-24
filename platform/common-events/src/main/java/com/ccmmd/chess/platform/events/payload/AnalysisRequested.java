package com.ccmmd.chess.platform.events.payload;

/** Request for a post-game analysis report. */
public record AnalysisRequested(
        String gameId,
        String requestedBy
) {
}
