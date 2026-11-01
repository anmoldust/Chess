package com.ccmmd.chess.matchmakingservice.repository;

import com.ccmmd.chess.matchmakingservice.domain.RatingChange;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RatingChangeRepository extends JpaRepository<RatingChange, String> {

    /** The idempotency backstop: a game is rated once, even if its event is redelivered. */
    boolean existsByGameId(String gameId);

    List<RatingChange> findByUserIdOrderByPlayedAtDesc(String userId, Pageable pageable);
}
