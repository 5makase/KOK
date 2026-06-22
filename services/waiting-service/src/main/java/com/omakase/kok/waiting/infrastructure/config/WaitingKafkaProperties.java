package com.omakase.kok.waiting.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "waiting.kafka.topic.waiting-events")
public record WaitingKafkaProperties(
        String name,
        int partitions,
        short replicas
) {
}
