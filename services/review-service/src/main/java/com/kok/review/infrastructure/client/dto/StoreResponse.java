package com.kok.review.infrastructure.client.dto;

import java.util.UUID;

public record StoreResponse(
        UUID storeId,
        String storeName,
        UUID ownerId
) {}
