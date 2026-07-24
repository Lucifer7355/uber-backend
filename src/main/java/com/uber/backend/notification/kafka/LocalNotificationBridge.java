package com.uber.backend.notification.kafka;

import com.uber.backend.config.UberProperties;
import com.uber.backend.event.bus.InMemoryEventPublisher;
import com.uber.backend.notification.service.NotificationService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(InMemoryEventPublisher.class)
public class LocalNotificationBridge {

    private static final Logger log = LoggerFactory.getLogger(LocalNotificationBridge.class);

    private final InMemoryEventPublisher publisher;
    private final NotificationService notificationService;
    private final UberProperties props;

    public LocalNotificationBridge(
            InMemoryEventPublisher publisher,
            NotificationService notificationService,
            UberProperties props) {
        this.publisher = publisher;
        this.notificationService = notificationService;
        this.props = props;
    }

    @PostConstruct
    void subscribe() {
        publisher.subscribe(props.kafka().topics().notifications(), event -> {
            if ("NOTIFICATION".equals(event.type())) {
                notificationService.handleNotificationEvent(event);
            }
        });
        log.info("local notification bridge subscribed to {}", props.kafka().topics().notifications());
    }
}
