package com.ccmmd.chess.gameservice.repository;

import com.ccmmd.chess.gameservice.domain.Game;
import com.ccmmd.chess.gameservice.domain.GameStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameRepository extends JpaRepository<Game, String> {

    List<Game> findByStatusOrderByCreatedAtDesc(GameStatus status);

    List<Game> findByStatusInOrderByCreatedAtDesc(List<GameStatus> statuses);
}
