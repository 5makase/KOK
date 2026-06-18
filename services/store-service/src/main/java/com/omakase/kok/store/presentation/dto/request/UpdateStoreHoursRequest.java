package com.omakase.kok.store.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalTime;

@Getter
public class UpdateStoreHoursRequest {

    private LocalTime openTime;

    private LocalTime closeTime;

    private LocalTime breakStartTime;

    private LocalTime breakEndTime;

    @NotNull
    private Boolean isDayOff;
}
