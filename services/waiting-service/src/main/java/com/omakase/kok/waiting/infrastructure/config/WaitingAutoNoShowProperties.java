package com.omakase.kok.waiting.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "waiting.auto-no-show")
public record WaitingAutoNoShowProperties(
        @NotBlank
        String cron,

        @Min(1)
        int batchSize
) {
}
