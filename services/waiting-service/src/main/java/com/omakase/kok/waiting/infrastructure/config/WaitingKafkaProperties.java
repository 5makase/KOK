package com.omakase.kok.waiting.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "waiting.kafka.topic.waiting-events")
public record WaitingKafkaProperties(
        @NotBlank
        String name,

        @Min(1)
        int partitions,

        @Min(1)
        short replicas
) {
}
