package com.omakase.kok.waiting.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "waiting.kafka.dlt.store-events")
public record StoreEventDltProperties(
        @NotBlank
        String name,

        @Min(0)
        long retryIntervalMs,

        @Min(0)
        long maxRetryAttempts,

        @Min(1)
        int partitions,

        @Min(1)
        short replicas
) {
}
