package com.uber.backend.notification.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TripWebSocketHandler tripWebSocketHandler;
    private final UserIdHandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(
            TripWebSocketHandler tripWebSocketHandler,
            UserIdHandshakeInterceptor handshakeInterceptor) {
        this.tripWebSocketHandler = tripWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(tripWebSocketHandler, "/ws/trips/{userId}")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
