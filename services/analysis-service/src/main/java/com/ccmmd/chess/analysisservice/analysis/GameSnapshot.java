package com.ccmmd.chess.analysisservice.analysis;

import java.util.List;

/** What analysis needs from a finished game. Fetched over HTTP; game-service owns it. */
public record GameSnapshot(
        String gameId,
        String status,
        String result,
        String whiteUserId,
        String blackUserId,
        List<String> fensAfterEachPly) {
}
