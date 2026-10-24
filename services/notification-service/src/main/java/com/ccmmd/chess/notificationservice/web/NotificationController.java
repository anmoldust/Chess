package com.ccmmd.chess.notificationservice.web;

import com.ccmmd.chess.notificationservice.dto.NotificationDto;
import com.ccmmd.chess.notificationservice.service.NotificationService;
import com.ccmmd.chess.platform.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    /** Always the caller's own notifications — the user id comes from the token, never a query. */
    @GetMapping
    public List<NotificationDto> mine(Authentication authentication) {
        return notifications.forUser(CurrentUser.id(authentication));
    }
}
