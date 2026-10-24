package com.ccmmd.chess.platform.events.payload;

import java.time.Instant;

/** Analysis finished. Accuracy is 0-100; blunders counts moves losing more than a threshold. */
public record AnalysisCompleted(
        String gameId,
        double accuracyWhite,
        double accuracyBlack,
        int blundersWhite,
        int blundersBlack,
        Instant completedAt
) {
}
