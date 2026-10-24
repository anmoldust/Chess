package com.ccmmd.chess.presenceservice.service;

import com.ccmmd.chess.presenceservice.dto.PresenceDto;
import com.ccmmd.chess.platform.amqp.RabbitNames;
import com.ccmmd.chess.platform.events.EventEnvelope;
import com.ccmmd.chess.platform.events.EventType;
import com.ccmmd.chess.platform.events.payload.PresenceUpdated;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Online/in-game state, held in Redis with a TTL.
 *
 * <p>"Online" is a heartbeat, not a connection: the WebSocket lives in game-service, and presence
 * must not need to observe it. A player who stops heartbeating simply expires — which also means
 * there is no "offline" event to publish; absence is the signal.
 *
 * <p>Notifications are published directly rather than through an outbox. This service commits no
 * durable state, and a lost presence event is self-healing on the next heartbeat, so at-most-once
 * is the right trade here. Every other service publishes through the outbox.
 */
@Service
public class PresenceService {

    private static final String PRODUCER = "presence-service";

    private final StringRedisTemplate redis;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public PresenceService(
            StringRedisTemplate redis,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${ccmmd.presence.ttl:PT90S}") Duration ttl) {
        this.redis = redis;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    /** Records a heartbeat, preserving any in-game marker already set. */
    public PresenceDto heartbeat(String userId) {
        requireSignedIn(userId);
        String key = key(userId);

        Boolean existed = redis.expire(key, ttl);
        if (!Boolean.TRUE.equals(existed)) {
            redis.opsForValue().set(key, "", ttl);
            publish(userId, true, null);
        }

        return status(userId);
    }

    /** Marks a player as mid-game; the marker expires with the heartbeat if they go quiet. */
    public void markInGame(String userId, String gameId) {
        if (userId == null || gameId == null) {
            return;
        }
        redis.opsForValue().set(key(userId), gameId, ttl);
        publish(userId, true, gameId);
    }

    public PresenceDto status(String userId) {
        requireSignedIn(userId);
        String gameId = redis.opsForValue().get(key(userId));
        if (gameId == null) {
            return new PresenceDto(userId, false, null);
        }
        return new PresenceDto(userId, true, gameId.isBlank() ? null : gameId);
    }

    /** Explicit sign-out. Without this, expiry is the only way a player goes offline. */
    public void offline(String userId) {
        if (userId == null) {
            return;
        }
        redis.delete(key(userId));
        publish(userId, false, null);
    }

    private void publish(String userId, boolean online, String gameId) {
        try {
            EventEnvelope envelope = EventEnvelope.of(
                    EventType.PRESENCE_UPDATED,
                    PRODUCER,
                    userId,
                    null,
                    new PresenceUpdated(userId, online, gameId, Instant.now()),
                    objectMapper);

            rabbitTemplate.convertAndSend(RabbitNames.EVENTS_EXCHANGE, EventType.PRESENCE_UPDATED, envelope);
        } catch (RuntimeException failure) {
            // Best-effort by design: presence is ephemeral and the next heartbeat re-announces it.
            // A broker hiccup must not break the heartbeat endpoint.
        }
    }

    private static String key(String userId) {
        return "presence:user:" + userId;
    }

    private static void requireSignedIn(String userId) {
        if (userId == null) {
            throw new AccessDeniedException("Sign in to use presence");
        }
    }
}
