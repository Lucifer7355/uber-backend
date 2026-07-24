package com.uber.backend.notification.model;

import java.time.Instant;

public record NotificationMessage(
        String notificationId,
        String tripId,
        String riderId,
        String driverId,
        String audience,
        String message,
        Instant sentAt
) {
}
