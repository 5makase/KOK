package com.omakase.kok.reservation.application.dto;

import jakarta.validation.constraints.AssertTrue;
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

    @AssertTrue(message = "예약금이 필요한 슬롯은 depositAmount가 0보다 커야 합니다.")
    public boolean isDepositAmountValid() {
        if (!depositRequired) {
            return true;
        }
        return depositAmount != null && depositAmount > 0;
    }
}
