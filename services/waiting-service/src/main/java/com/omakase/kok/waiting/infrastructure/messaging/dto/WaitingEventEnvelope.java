package com.omakase.kok.waiting.infrastructure.messaging.dto;

import com.omakase.kok.waiting.domain.enums.WaitingEventType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class WaitingEventEnvelope<T> {
    private final UUID eventId;
    private final WaitingEventType eventType;
    private final int schemaVersion;
    private final LocalDateTime occurredAt;
    private final String producer;
    private final T payload;
}
