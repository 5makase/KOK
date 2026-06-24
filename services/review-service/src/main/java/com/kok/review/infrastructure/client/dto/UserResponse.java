package com.kok.review.infrastructure.client.dto;

import java.util.UUID;

public record UserResponse(
        UUID userId,
        String name
) {}
