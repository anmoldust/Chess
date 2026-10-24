package com.ccmmd.chess.matchmakingservice.repository;

import com.ccmmd.chess.matchmakingservice.domain.MatchRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatchRepository extends JpaRepository<MatchRecord, String> {

    Optional<MatchRecord> findFirstByWhiteUserIdOrBlackUserIdOrderByMatchedAtDesc(
            String whiteUserId, String blackUserId);

    boolean existsByGameId(String gameId);
}
