package com.ccmmd.chess.analysisservice.domain;

import com.ccmmd.chess.analysisservice.analysis.AnalysisCalculator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "analysis_reports")
public class AnalysisReport {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "game_id", nullable = false, unique = true, length = 36)
    private String gameId;

    @Column(name = "accuracy_white", nullable = false)
    private double accuracyWhite;

    @Column(name = "accuracy_black", nullable = false)
    private double accuracyBlack;

    @Column(name = "blunders_white", nullable = false)
    private int blundersWhite;

    @Column(name = "blunders_black", nullable = false)
    private int blundersBlack;

    @Column(name = "moves_analysed", nullable = false)
    private int movesAnalysed;

    /** False means the numbers are the material heuristic, not an engine evaluation. */
    @Column(name = "engine_backed", nullable = false)
    private boolean engineBacked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AnalysisReport() {
    }

    public AnalysisReport(String id, String gameId, AnalysisCalculator.Report report, boolean engineBacked) {
        this.id = id;
        this.gameId = gameId;
        this.accuracyWhite = report.accuracyWhite();
        this.accuracyBlack = report.accuracyBlack();
        this.blundersWhite = report.blundersWhite();
        this.blundersBlack = report.blundersBlack();
        this.movesAnalysed = report.movesAnalysed();
        this.engineBacked = engineBacked;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getGameId() {
        return gameId;
    }

    public double getAccuracyWhite() {
        return accuracyWhite;
    }

    public double getAccuracyBlack() {
        return accuracyBlack;
    }

    public int getBlundersWhite() {
        return blundersWhite;
    }

    public int getBlundersBlack() {
        return blundersBlack;
    }

    public int getMovesAnalysed() {
        return movesAnalysed;
    }

    public boolean isEngineBacked() {
        return engineBacked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
