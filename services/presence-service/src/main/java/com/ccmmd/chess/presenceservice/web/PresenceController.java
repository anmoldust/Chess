package com.ccmmd.chess.presenceservice.web;

import com.ccmmd.chess.platform.security.CurrentUser;
import com.ccmmd.chess.presenceservice.dto.PresenceDto;
import com.ccmmd.chess.presenceservice.service.PresenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/presence")
public class PresenceController {

    private final PresenceService presence;

    public PresenceController(PresenceService presence) {
        this.presence = presence;
    }

    @PostMapping("/heartbeat")
    public PresenceDto heartbeat(Authentication authentication) {
        return presence.heartbeat(CurrentUser.id(authentication));
    }

    @PostMapping("/offline")
    public ResponseEntity<Void> offline(Authentication authentication) {
        presence.offline(CurrentUser.id(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}")
    public PresenceDto status(@PathVariable String userId) {
        return presence.status(userId);
    }
}
