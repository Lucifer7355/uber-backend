package com.uber.backend.event.config;

import com.uber.backend.config.UberProperties;
import com.uber.backend.event.bus.EventPublisher;
import com.uber.backend.event.bus.InMemoryEventPublisher;
import com.uber.backend.event.bus.KafkaEventPublisher;
import com.uber.backend.event.model.DomainEvent;
import com.uber.backend.location.repository.DriverLocationRepository;
import com.uber.backend.location.repository.InMemoryDriverLocationRepository;
import com.uber.backend.location.repository.RedisDriverLocationRepository;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class InfrastructureConfig {

    @Bean
    @Profile("local")
    public DriverLocationRepository inMemoryDriverLocationRepository(UberProperties props) {
        return new InMemoryDriverLocationRepository(props.surge().geohashPrecision());
    }

    @Bean
    @Profile("!local")
    public DriverLocationRepository redisDriverLocationRepository(
            StringRedisTemplate redis, UberProperties props) {
        return new RedisDriverLocationRepository(redis, props);
    }

    @Bean
    @Profile("local")
    public InMemoryEventPublisher inMemoryEventPublisher() {
        return new InMemoryEventPublisher();
    }

    @Bean
    @Profile("!local")
    public EventPublisher kafkaEventPublisher(KafkaTemplate<String, DomainEvent> kafkaTemplate) {
        return new KafkaEventPublisher(kafkaTemplate);
    }

    @Bean
    @Profile("!local")
    public NewTopic locationUpdatesTopic(UberProperties props) {
        return TopicBuilder.name(props.kafka().topics().locationUpdates()).partitions(3).replicas(1).build();
    }

    @Bean
    @Profile("!local")
    public NewTopic tripEventsTopic(UberProperties props) {
        return TopicBuilder.name(props.kafka().topics().tripEvents()).partitions(3).replicas(1).build();
    }

    @Bean
    @Profile("!local")
    public NewTopic notificationsTopic(UberProperties props) {
        return TopicBuilder.name(props.kafka().topics().notifications()).partitions(3).replicas(1).build();
    }
}
