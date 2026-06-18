package com.omakase.kok.reservation.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class CreateSlotRequest {

    @NotNull
    private UUID storeId;

    @NotNull
    private LocalDate slotDate;

    @NotNull
    private LocalTime slotTime;

    @Min(1)
    private int maxCapacity;

    private boolean depositRequired;

    private Long depositAmount;
}
