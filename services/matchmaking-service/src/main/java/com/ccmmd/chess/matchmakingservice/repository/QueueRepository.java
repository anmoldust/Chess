package com.ccmmd.chess.matchmakingservice.repository;

import com.ccmmd.chess.matchmakingservice.domain.QueueEntry;
import com.ccmmd.chess.matchmakingservice.domain.QueueStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QueueRepository extends JpaRepository<QueueEntry, String> {

    Optional<QueueEntry> findFirstByUserIdAndStatus(String userId, QueueStatus status);

    Optional<QueueEntry> findFirstByUserIdOrderByQueuedAtDesc(String userId);

    List<QueueEntry> findByStatusOrderByQueuedAtAsc(QueueStatus status);

    List<QueueEntry> findTop2ByStatusAndTimeControlOrderByQueuedAtAsc(QueueStatus status, String timeControl);

    /**
     * Row-level lock for the matcher. Two instances must not pair the same player, so the pair is
     * re-read under {@code SELECT ... FOR UPDATE} before either entry is claimed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QueueEntry q where q.id = :id")
    Optional<QueueEntry> findByIdForUpdate(@Param("id") String id);
}
