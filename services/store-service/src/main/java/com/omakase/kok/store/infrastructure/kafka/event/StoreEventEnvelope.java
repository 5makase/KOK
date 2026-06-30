package com.omakase.kok.store.infrastructure.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class StoreEventEnvelope<T> {
    private final UUID eventId;
    private final String eventType;
    private final int schemaVersion;
    private final LocalDateTime occurredAt;
    private final String producer;
    private final T payload;
}
