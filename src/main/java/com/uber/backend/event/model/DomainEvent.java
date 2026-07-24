package com.uber.backend.event.model;

import java.time.Instant;
import java.util.Map;

public record DomainEvent(
        String eventId,
        String type,
        String aggregateId,
        Instant occurredAt,
        Map<String, Object> payload
) {
}
