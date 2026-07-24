package com.uber.backend.notification.kafka;

import com.uber.backend.event.model.DomainEvent;
import com.uber.backend.notification.service.NotificationService;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
public class KafkaNotificationConsumer {

    private final NotificationService notificationService;

    public KafkaNotificationConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "${uber.kafka.topics.notifications}", groupId = "uber-notifications")
    public void onMessage(DomainEvent event) {
        if ("NOTIFICATION".equals(event.type())) {
            notificationService.handleNotificationEvent(event);
        }
    }
}
