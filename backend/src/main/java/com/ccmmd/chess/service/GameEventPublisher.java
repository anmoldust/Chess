package com.ccmmd.chess.service;

import com.ccmmd.chess.dto.ErrorDto;
import com.ccmmd.chess.dto.GameDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Pushes game state to the per-game STOMP topic. */
@Component
public class GameEventPublisher {

    private final SimpMessagingTemplate messaging;

    public GameEventPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void gameUpdated(GameDto dto) {
        messaging.convertAndSend("/topic/game/" + dto.id(), dto);
    }

    public void reject(String gameId, String code, String detail) {
        messaging.convertAndSend("/topic/game/" + gameId + "/errors", new ErrorDto(code, detail));
    }
}
