package com.ccmmd.chess.matchmakingservice.repository;

import com.ccmmd.chess.matchmakingservice.domain.Rating;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RatingRepository extends JpaRepository<Rating, String> {

    /** The leaderboard: ranked players only, so an account that has never finished a game is not on it. */
    List<Rating> findByGamesPlayedGreaterThanOrderByRatingDesc(int gamesPlayed, Pageable pageable);
}
