package com.ccmmd.chess.notificationservice.dto;

import java.time.Instant;

public record NotificationDto(String id, String kind, String message, Instant createdAt) {
}
