package com.ccmmd.chess.platform.messaging;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The outbox relay is a scheduled job: without scheduling, an event would be written to the
 * outbox and never sent — the exact failure the outbox exists to prevent.
 */
@Configuration
@EnableScheduling
public class OutboxSchedulingConfig {
}
