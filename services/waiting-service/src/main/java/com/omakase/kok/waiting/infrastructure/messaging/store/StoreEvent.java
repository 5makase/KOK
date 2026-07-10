package com.omakase.kok.waiting.infrastructure.messaging.store;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class StoreEvent {
    private UUID eventId;
    private StoreEventType eventType;
    private Integer schemaVersion;
    private LocalDateTime occurredAt;
    private String producer;
    private Payload payload;

    @Getter
    @NoArgsConstructor
    public static class Payload {
        private UUID storeId;
    }
}
