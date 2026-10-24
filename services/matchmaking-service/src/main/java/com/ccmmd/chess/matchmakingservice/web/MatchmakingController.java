package com.ccmmd.chess.matchmakingservice.web;

import com.ccmmd.chess.matchmakingservice.dto.MatchmakingStatusDto;
import com.ccmmd.chess.matchmakingservice.dto.QueueRequest;
import com.ccmmd.chess.matchmakingservice.dto.QueueStatusDto;
import com.ccmmd.chess.matchmakingservice.service.MatchmakingService;
import com.ccmmd.chess.platform.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matchmaking")
public class MatchmakingController {

    private final MatchmakingService matchmaking;

    public MatchmakingController(MatchmakingService matchmaking) {
        this.matchmaking = matchmaking;
    }

    @PostMapping("/queue")
    public QueueStatusDto queue(
            @RequestBody(required = false) QueueRequest request, Authentication authentication) {
        return matchmaking.enqueue(
                CurrentUser.id(authentication),
                CurrentUser.displayName(authentication),
                request == null ? null : request.timeControl());
    }

    @DeleteMapping("/queue")
    public ResponseEntity<Void> leave(Authentication authentication) {
        matchmaking.cancel(CurrentUser.id(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status")
    public MatchmakingStatusDto status(Authentication authentication) {
        return matchmaking.status(CurrentUser.id(authentication));
    }
}
