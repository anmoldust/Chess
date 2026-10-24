package com.ccmmd.chess.analysisservice.service;

import com.ccmmd.chess.analysisservice.analysis.AnalysisCalculator;
import com.ccmmd.chess.analysisservice.analysis.GameSnapshot;
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

    public AnalysisService(AnalysisReportRepository repository, OutboxPublisher outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    /**
     * Analyses a finished game and records the report.
     *
     * <p>Idempotent on the game id: a redelivered {@code game.ended} must not produce a second
     * report, and the database's unique constraint on {@code game_id} is the backstop.
     */
    @Transactional
    public Optional<AnalysisReport> analyse(GameSnapshot game) {
        if (repository.findByGameId(game.gameId()).isPresent()) {
            return Optional.empty();
        }

        AnalysisCalculator.Report report = AnalysisCalculator.review(game.fensAfterEachPly());
        AnalysisReport saved = repository.saveAndFlush(new AnalysisReport(
                UUID.randomUUID().toString(), game.gameId(), report, false));

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
