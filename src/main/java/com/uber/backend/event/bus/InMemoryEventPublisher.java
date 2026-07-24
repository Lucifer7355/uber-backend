package com.uber.backend.event.bus;

import com.uber.backend.event.model.DomainEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-process pub/sub that mirrors Kafka topics for local profile and tests.
 * Thread-safe: CopyOnWriteArrayList for subscribers, ConcurrentHashMap for topic index.
 */
public final class InMemoryEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEventPublisher.class);

    private final Map<String, List<Consumer<DomainEvent>>> subscribers = new ConcurrentHashMap<>();
    private final List<DomainEvent> history = new CopyOnWriteArrayList<>();

    @Override
    public void publish(String topic, String key, DomainEvent event) {
        history.add(event);
        log.debug("in-memory publish topic={} key={} type={}", topic, key, event.type());
        List<Consumer<DomainEvent>> consumers = subscribers.getOrDefault(topic, List.of());
        for (Consumer<DomainEvent> consumer : consumers) {
            consumer.accept(event);
        }
    }

    public void subscribe(String topic, Consumer<DomainEvent> consumer) {
        subscribers.computeIfAbsent(topic, t -> new CopyOnWriteArrayList<>()).add(consumer);
    }

    public List<DomainEvent> history() {
        return List.copyOf(history);
    }

    public void clear() {
        history.clear();
        subscribers.clear();
    }
}
