package com.uber.backend.notification.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.backend.notification.model.NotificationMessage;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Real-time push channel. Clients connect to /ws/trips/{userId}.
 * Fan-out is concurrent-safe via CopyOnWriteArraySet per user.
 */
@Component
public class TripWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TripWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public TripWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = userId(session);
        sessionsByUser.computeIfAbsent(userId, id -> new CopyOnWriteArraySet<>()).add(session);
        log.info("websocket connected user={} session={}", userId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = userId(session);
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionsByUser.remove(userId);
            }
        }
        log.info("websocket closed user={} session={}", userId, session.getId());
    }

    public void push(String userId, NotificationMessage message) {
        Set<WebSocketSession> sessions = sessionsByUser.getOrDefault(userId, Set.of());
        if (sessions.isEmpty()) {
            log.debug("no websocket sessions for user={}", userId);
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(message);
            TextMessage text = new TextMessage(json);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(text);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("failed to push websocket message to {}: {}", userId, e.getMessage());
        }
    }

    public int connectedUsers() {
        return sessionsByUser.size();
    }

    private static String userId(WebSocketSession session) {
        Object attr = session.getAttributes().get("userId");
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        String path = session.getUri() == null ? "" : session.getUri().getPath();
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : "anonymous";
    }
}
