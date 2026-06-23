package com.omakase.kok.waiting.infrastructure.client.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(staticName = "of")
public class StoreSummaryResponse {
    private UUID storeId;
    private String storeName;
    private UUID ownerId;
}
