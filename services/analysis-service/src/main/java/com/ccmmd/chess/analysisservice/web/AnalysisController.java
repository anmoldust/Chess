package com.ccmmd.chess.analysisservice.web;

import com.ccmmd.chess.analysisservice.domain.AnalysisReport;
import com.ccmmd.chess.analysisservice.service.AnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysis;

    public AnalysisController(AnalysisService analysis) {
        this.analysis = analysis;
    }

    @GetMapping("/{gameId}")
    public ResponseEntity<AnalysisReportDto> report(@PathVariable String gameId) {
        return analysis.forGame(gameId)
                .map(AnalysisController::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static AnalysisReportDto toDto(AnalysisReport report) {
        return new AnalysisReportDto(
                report.getGameId(),
                report.getAccuracyWhite(),
                report.getAccuracyBlack(),
                report.getBlundersWhite(),
                report.getBlundersBlack(),
                report.getMovesAnalysed(),
                report.isEngineBacked(),
                report.getCreatedAt());
    }

    /** {@code engineBacked=false} means these are material counts, not an engine evaluation. */
    public record AnalysisReportDto(
            String gameId,
            double accuracyWhite,
            double accuracyBlack,
            int blundersWhite,
            int blundersBlack,
            int movesAnalysed,
            boolean engineBacked,
            java.time.Instant createdAt) {
    }
}
