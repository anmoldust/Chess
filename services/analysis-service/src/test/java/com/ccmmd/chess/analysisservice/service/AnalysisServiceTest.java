package com.ccmmd.chess.analysisservice.service;

import com.ccmmd.chess.analysisservice.analysis.GameSnapshot;
import com.ccmmd.chess.analysisservice.domain.AnalysisReport;
import com.ccmmd.chess.platform.events.EventType;
import com.ccmmd.chess.platform.messaging.OutboxEntry;
import com.ccmmd.chess.platform.messaging.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AnalysisServiceTest {

    private static final String BALANCED =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String WHITE_MINUS_QUEEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNB1KBNR w KQkq - 0 1";

    @Autowired
    private AnalysisService analysis;

    @Autowired
    private OutboxRepository outbox;

    private static GameSnapshot game(String gameId) {
        return new GameSnapshot(
                gameId, "WHITE_WON", "1-0", "user-w", "user-b",
                List.of(BALANCED, WHITE_MINUS_QUEEN));
    }

    @Test
    @DisplayName("analysing a game stores a report and queues analysis.completed")
    void analyseStoresReport() {
        String gameId = UUID.randomUUID().toString();

        AnalysisReport report = analysis.analyse(game(gameId)).orElseThrow();

        assertThat(report.getGameId()).isEqualTo(gameId);
        assertThat(report.getMovesAnalysed()).isEqualTo(2);
        assertThat(report.getBlundersWhite()).isEqualTo(1);
        // Honest labelling: this is the material heuristic, not an engine evaluation.
        assertThat(report.isEngineBacked()).isFalse();
        assertThat(outbox.findAll()).extracting(OutboxEntry::getType)
                .contains(EventType.ANALYSIS_COMPLETED);
    }

    @Test
    @DisplayName("a redelivered game.ended does not produce a second report")
    void duplicateGameIsIgnored() {
        String gameId = UUID.randomUUID().toString();

        assertThat(analysis.analyse(game(gameId))).isPresent();
        assertThat(analysis.analyse(game(gameId))).isEmpty();
        assertThat(analysis.forGame(gameId)).isPresent();
    }

    @Test
    @DisplayName("an unanalysed game has no report")
    void noReportForUnknownGame() {
        assertThat(analysis.forGame(UUID.randomUUID().toString())).isEmpty();
    }
}
