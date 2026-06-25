package com.omakase.kok.waiting.infrastructure.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class WaitingEnteredEvent {
    private final UUID waitingId;
    private final UUID storeId;
    private final String storeName;
    private final UUID userId;
    private final Long waitingNumber;
}
