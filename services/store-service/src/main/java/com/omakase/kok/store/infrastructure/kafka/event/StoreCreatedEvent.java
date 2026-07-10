package com.omakase.kok.store.infrastructure.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class StoreCreatedEvent {
    private final UUID storeId;
}