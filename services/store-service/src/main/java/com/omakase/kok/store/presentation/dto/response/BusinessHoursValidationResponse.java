package com.omakase.kok.store.presentation.dto.response;

public record BusinessHoursValidationResponse(boolean available, String reason) {

    public static BusinessHoursValidationResponse ok() {
        return new BusinessHoursValidationResponse(true, null);
    }

    public static BusinessHoursValidationResponse denied(String reason) {
        return new BusinessHoursValidationResponse(false, reason);
    }
}
