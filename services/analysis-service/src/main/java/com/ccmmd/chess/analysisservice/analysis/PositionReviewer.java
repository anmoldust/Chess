package com.ccmmd.chess.analysisservice.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Produces an engine-backed review when a UCI engine is configured, and returns empty when it is
 * not — leaving the caller to fall back to the material heuristic.
 *
 * <p>The engine is the same kind analysis-service now drives in Docker (Stockfish, baked into the
 * image). Absence of a binary is not an error: the report is still produced, just honest about
 * being an approximation. A failing engine is treated the same way for the same reason — a broken
 * engine should not cost the user their analysis.
 */
@Component
public class PositionReviewer {

    private static final Logger log = LoggerFactory.getLogger(PositionReviewer.class);

    /** The canonical start position, prepended so the first move has a position before it. */
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private final EngineEvaluation engine;
    private final int depth;

    public PositionReviewer(
            @Value("${ccmmd.engine.binary:}") String binary,
            @Value("${ccmmd.engine.startup-timeout-ms:15000}") long startupTimeoutMs,
            @Value("${ccmmd.engine.eval-timeout-ms:15000}") long evalTimeoutMs,
            @Value("${ccmmd.engine.depth:12}") int depth) {
        this.depth = depth;

        if (binary == null || binary.isBlank()) {
            log.warn("ccmmd.engine.binary is not set — analysis falls back to the material heuristic "
                    + "(engineBacked=false). Point it at a UCI engine (e.g. Stockfish) for a real evaluation.");
            this.engine = null;
        } else {
            List<String> command = Arrays.stream(binary.trim().split("\\s+")).toList();
            this.engine = new UciEvaluationEngine(
                    command, Duration.ofMillis(startupTimeoutMs), Duration.ofMillis(evalTimeoutMs));
            log.info("analysis engine configured: {} (depth {})", binary, depth);
        }
    }

    /**
     * @param fensAfterEachPly the position after every ply, without the start position
     * @return an engine review, or empty when no engine is configured or it failed
     */
    public Optional<AnalysisCalculator.Report> review(List<String> fensAfterEachPly) {
        if (engine == null || fensAfterEachPly.isEmpty()) {
            return Optional.empty();
        }

        List<String> positions = new ArrayList<>(fensAfterEachPly.size() + 1);
        positions.add(START_FEN);
        positions.addAll(fensAfterEachPly);

        try {
            return Optional.of(EngineReview.review(positions, engine, depth));
        } catch (RuntimeException failure) {
            log.warn("engine review failed; falling back to the material heuristic", failure);
            return Optional.empty();
        }
    }

    /** Whether an engine is actually wired up. Used by the report's honest labelling. */
    public boolean hasEngine() {
        return engine != null;
    }
}
