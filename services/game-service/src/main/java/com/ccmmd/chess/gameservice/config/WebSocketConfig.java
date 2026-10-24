package com.ccmmd.chess.gameservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Raw WebSocket rather than SockJS: the only client is the gateway, which proxies a normal
 * upgrade and attaches the Bearer token the browser could not set itself.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // A WebSocket handshake is checked against its Origin, and the gateway forwards the
        // browser's (http://localhost:3000) — which never matches this service's own host, so
        // Spring rejects it with 403 and the browser reports only "offline". The service publishes
        // no ports, so the gateway is the only thing that can reach it; the check adds nothing here
        // and breaks the proxied handshake.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic");
    }
}
