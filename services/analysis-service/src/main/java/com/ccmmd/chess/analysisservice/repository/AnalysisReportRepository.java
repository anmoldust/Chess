package com.ccmmd.chess.analysisservice.repository;

import com.ccmmd.chess.analysisservice.domain.AnalysisReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, String> {

    Optional<AnalysisReport> findByGameId(String gameId);
}
