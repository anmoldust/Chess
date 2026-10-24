package com.ccmmd.chess.notificationservice.service;

import com.ccmmd.chess.notificationservice.domain.Notification;
import com.ccmmd.chess.notificationservice.dto.NotificationDto;
import com.ccmmd.chess.notificationservice.repository.NotificationRepository;
import com.ccmmd.chess.platform.events.EventType;
import com.ccmmd.chess.platform.events.payload.NotificationSent;
import com.ccmmd.chess.platform.messaging.OutboxPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private static final String PRODUCER = "notification-service";

    private final NotificationRepository repository;
    private final OutboxPublisher outbox;

    public NotificationService(NotificationRepository repository, OutboxPublisher outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    /**
     * Records one notification and announces it.
     *
     * <p>Called from inside the consuming handler, so this joins that transaction: the
     * notification row and its outbox row commit together, and a handler failure rolls both back
     * and lets the broker redeliver.
     */
    @Transactional
    public Notification record(String userId, String kind, String message) {
        Notification notification = new Notification(
                UUID.randomUUID().toString(), userId, kind, truncate(message));
        repository.saveAndFlush(notification);

        outbox.publish(
                EventType.NOTIFICATION_SENT,
                PRODUCER,
                notification.getId(),
                null,
                new NotificationSent(
                        notification.getId(),
                        userId,
                        kind,
                        notification.getMessage(),
                        notification.getCreatedAt()));

        return notification;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> forUser(String userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(n -> new NotificationDto(n.getId(), n.getKind(), n.getMessage(), n.getCreatedAt()))
                .toList();
    }

    private static String truncate(String message) {
        if (message == null) {
            return "";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
