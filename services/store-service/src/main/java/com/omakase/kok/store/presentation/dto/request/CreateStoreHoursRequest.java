package com.omakase.kok.store.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Getter
public class CreateStoreHoursRequest {

    @NotNull
    private DayOfWeek dayOfWeek;

    private LocalTime openTime;

    private LocalTime closeTime;

    private LocalTime breakStartTime;

    private LocalTime breakEndTime;

    @NotNull
    private Boolean isDayOff;
}
