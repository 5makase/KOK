package com.omakase.kok.store.infrastructure.kafka.event;

import java.util.Optional;

public enum ReviewEventType {
    REVIEW_CREATED,
    REVIEW_UPDATED,
    REVIEW_DELETED;

    public static Optional<ReviewEventType> from(String value) {
        try {
            return Optional.of(valueOf(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
