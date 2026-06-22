package com.omakase.kok.waiting.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "waiting.kafka.publisher")
public record WaitingKafkaPublisherProperties(
        long fixedDelayMs,
        int batchSize
) {
}
