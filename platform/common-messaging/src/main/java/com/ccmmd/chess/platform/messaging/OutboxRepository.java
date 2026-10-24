package com.ccmmd.chess.platform.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEntry, String> {

    List<OutboxEntry> findTop200ByPublishedAtIsNullOrderByOccurredAtAsc();

    long countByPublishedAtIsNull();
}
