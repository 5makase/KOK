package com.omakase.kok.waiting.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "waiting.kafka.publisher")
public record WaitingKafkaPublisherProperties(
        @Min(1)
        long fixedDelayMs,

        @Min(1)
        long sendTimeoutMs,

        @Min(1)
        int batchSize
) {
}
