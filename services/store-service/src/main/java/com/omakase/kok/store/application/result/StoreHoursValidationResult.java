package com.omakase.kok.store.application.result;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StoreHoursValidationResult {

    private final boolean available;
    private final String reason;

    public static StoreHoursValidationResult ok() {
        return StoreHoursValidationResult.builder()
                .available(true)
                .build();
    }

    public static StoreHoursValidationResult denied(String reason) {
        return StoreHoursValidationResult.builder()
                .available(false)
                .reason(reason)
                .build();
    }
}
