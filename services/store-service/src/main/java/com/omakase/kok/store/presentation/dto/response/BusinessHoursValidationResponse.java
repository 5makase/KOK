package com.omakase.kok.store.presentation.dto.response;

import com.omakase.kok.store.application.result.StoreHoursValidationResult;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BusinessHoursValidationResponse {

    private final boolean available;
    private final String reason;

    public static BusinessHoursValidationResponse from(StoreHoursValidationResult result) {
        return BusinessHoursValidationResponse.builder()
                .available(result.isAvailable())
                .reason(result.getReason())
                .build();
    }
}
