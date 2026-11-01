package com.ccmmd.chess.analysisservice.service;

import com.ccmmd.chess.analysisservice.analysis.AnalysisCalculator;
import com.ccmmd.chess.analysisservice.analysis.GameSnapshot;
import com.ccmmd.chess.analysisservice.analysis.PositionReviewer;
import com.ccmmd.chess.analysisservice.domain.AnalysisReport;
import com.ccmmd.chess.analysisservice.repository.AnalysisReportRepository;
import com.ccmmd.chess.platform.events.EventType;
import com.ccmmd.chess.platform.events.payload.AnalysisCompleted;
import com.ccmmd.chess.platform.messaging.OutboxPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class AnalysisService {

    private static final String PRODUCER = "analysis-service";

    private final AnalysisReportRepository repository;
    private final OutboxPublisher outbox;
    private final PositionReviewer reviewer;

    public AnalysisService(
            AnalysisReportRepository repository, OutboxPublisher outbox, PositionReviewer reviewer) {
        this.repository = repository;
        this.outbox = outbox;
        this.reviewer = reviewer;
    }

    /**
     * Analyses a finished game and records the report.
     *
     * <p>Prefers an engine evaluation; when no engine is configured (or it fails) it falls back to
     * the material heuristic and records {@code engineBacked=false}, so the number is never passed
     * off as more than it is.
     *
     * <p>Idempotent on the game id: a redelivered {@code game.ended} must not produce a second
     * report, and the database's unique constraint on {@code game_id} is the backstop.
     */
    @Transactional
    public Optional<AnalysisReport> analyse(GameSnapshot game) {
        if (repository.findByGameId(game.gameId()).isPresent()) {
            return Optional.empty();
        }

        Optional<AnalysisCalculator.Report> engineReport = reviewer.review(game.fensAfterEachPly());
        AnalysisCalculator.Report report = engineReport
                .orElseGet(() -> AnalysisCalculator.review(game.fensAfterEachPly()));

        AnalysisReport saved = repository.saveAndFlush(new AnalysisReport(
                UUID.randomUUID().toString(), game.gameId(), report, engineReport.isPresent()));

        outbox.publish(
                EventType.ANALYSIS_COMPLETED,
                PRODUCER,
                saved.getGameId(),
                null,
                new AnalysisCompleted(
                        saved.getGameId(),
                        saved.getAccuracyWhite(),
                        saved.getAccuracyBlack(),
                        saved.getBlundersWhite(),
                        saved.getBlundersBlack(),
                        saved.getCreatedAt()));

        return Optional.of(saved);
    }

    @Transactional(readOnly = true)
    public Optional<AnalysisReport> forGame(String gameId) {
        return repository.findByGameId(gameId);
    }
}
