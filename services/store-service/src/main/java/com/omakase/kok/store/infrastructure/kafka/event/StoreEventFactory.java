package com.omakase.kok.store.infrastructure.kafka.event;

import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.enums.StoreEventType;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StoreEventFactory {

    private static final int SCHEMA_VERSION = 1;
    private static final String PRODUCER = "store-service";

    public StoreEventEnvelope<StoreCreatedEvent> createStoreCreatedEnvelope(UUID eventId, Store store) {
        StoreCreatedEvent payload = new StoreCreatedEvent(store.getStoreId());
        return new StoreEventEnvelope<>(
            eventId,
            StoreEventType.STORE_CREATED.name(),
            SCHEMA_VERSION,
            store.getCreatedAt(),
            PRODUCER,
            payload
        );
    }
}
