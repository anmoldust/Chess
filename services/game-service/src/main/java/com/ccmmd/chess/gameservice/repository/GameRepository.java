package com.ccmmd.chess.gameservice.repository;

import com.ccmmd.chess.gameservice.domain.Game;
import com.ccmmd.chess.gameservice.domain.GameStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GameRepository extends JpaRepository<Game, String> {

    List<Game> findByStatusOrderByCreatedAtDesc(GameStatus status);

    List<Game> findByStatus(GameStatus status);

    List<Game> findByStatusInOrderByCreatedAtDesc(List<GameStatus> statuses);

    /**
     * A player's games, newest first, restricted to the given statuses.
     *
     * <p>Explicit JPQL rather than a derived name: the {@code and}/{@code or} precedence in
     * {@code findByAOrBAndC} is easy to get wrong, and this reads unambiguously.
     */
    @Query("select g from Game g "
            + "where (g.whiteUserId = :userId or g.blackUserId = :userId) "
            + "and g.status in :statuses "
            + "order by g.createdAt desc")
    List<Game> findForPlayer(
            @Param("userId") String userId,
            @Param("statuses") List<GameStatus> statuses,
            Pageable pageable);
}
