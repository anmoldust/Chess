package com.ccmmd.chess.repository;

import com.ccmmd.chess.domain.Game;
import com.ccmmd.chess.domain.GameStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameRepository extends JpaRepository<Game, String> {

    List<Game> findByStatusOrderByCreatedAtDesc(GameStatus status);

    List<Game> findByStatusInOrderByCreatedAtDesc(List<GameStatus> statuses);
}
