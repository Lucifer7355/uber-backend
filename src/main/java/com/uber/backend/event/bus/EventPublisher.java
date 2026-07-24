package com.uber.backend.event.bus;

import com.uber.backend.event.model.DomainEvent;

/**
 * Abstraction over Kafka (or in-memory) so services stay infrastructure-agnostic.
 */
public interface EventPublisher {

    void publish(String topic, String key, DomainEvent event);
}
