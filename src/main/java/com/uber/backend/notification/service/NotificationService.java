package com.uber.backend.notification.service;

import com.uber.backend.common.id.IdGenerator;
import com.uber.backend.event.model.DomainEvent;
import com.uber.backend.notification.model.NotificationMessage;
import com.uber.backend.notification.websocket.TripWebSocketHandler;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final TripWebSocketHandler webSocketHandler;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final CopyOnWriteArrayList<NotificationMessage> history = new CopyOnWriteArrayList<>();

    public NotificationService(TripWebSocketHandler webSocketHandler, IdGenerator idGenerator, Clock clock) {
        this.webSocketHandler = webSocketHandler;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public void handleNotificationEvent(DomainEvent event) {
        Map<String, Object> payload = event.payload();
        String tripId = String.valueOf(payload.getOrDefault("tripId", ""));
        String riderId = String.valueOf(payload.getOrDefault("riderId", ""));
        String driverId = String.valueOf(payload.getOrDefault("driverId", ""));
        String audience = String.valueOf(payload.getOrDefault("audience", "BOTH"));
        String message = String.valueOf(payload.getOrDefault("message", ""));

        NotificationMessage notification = new NotificationMessage(
                idGenerator.nextId("ntf"),
                tripId,
                riderId,
                driverId,
                audience,
                message,
                clock.instant());
        history.add(notification);

        if ("RIDER".equals(audience) || "BOTH".equals(audience)) {
            if (!riderId.isBlank()) {
                webSocketHandler.push(riderId, notification);
            }
        }
        if ("DRIVER".equals(audience) || "BOTH".equals(audience)) {
            if (!driverId.isBlank()) {
                webSocketHandler.push(driverId, notification);
            }
        }
        log.info("notification trip={} audience={} msg={}", tripId, audience, message);
    }

    public List<NotificationMessage> recent(int limit) {
        int size = history.size();
        int from = Math.max(0, size - Math.max(1, limit));
        return new ArrayList<>(history.subList(from, size));
    }

    public void clear() {
        history.clear();
    }
}
