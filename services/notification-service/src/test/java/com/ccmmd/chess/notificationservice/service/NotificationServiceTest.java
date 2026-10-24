package com.ccmmd.chess.notificationservice.service;

import com.ccmmd.chess.notificationservice.dto.NotificationDto;
import com.ccmmd.chess.platform.events.EventType;
import com.ccmmd.chess.platform.messaging.OutboxEntry;
import com.ccmmd.chess.platform.messaging.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NotificationServiceTest {

    @Autowired
    private NotificationService notifications;

    @Autowired
    private OutboxRepository outbox;

    private static String user() {
        return UUID.randomUUID().toString();
    }

    @Test
    @DisplayName("recording a notification queues notification.sent in the same transaction")
    void recordPublishesEvent() {
        notifications.record(user(), "WELCOME", "Welcome aboard");

        assertThat(outbox.findAll()).extracting(OutboxEntry::getType)
                .contains(EventType.NOTIFICATION_SENT);
    }

    @Test
    @DisplayName("a user only ever sees their own notifications")
    void listsOnlyOwnNotifications() {
        String mine = user();
        String someoneElse = user();

        notifications.record(mine, "WELCOME", "Mine");
        notifications.record(someoneElse, "WELCOME", "Theirs");

        List<String> messages = notifications.forUser(mine).stream()
                .map(NotificationDto::message)
                .toList();

        assertThat(messages).containsExactly("Mine");
    }

    @Test
    @DisplayName("an over-long message is truncated rather than rejected")
    void truncatesLongMessages() {
        String userId = user();
        notifications.record(userId, "WELCOME", "x".repeat(900));

        assertThat(notifications.forUser(userId).get(0).message()).hasSize(500);
    }
}
