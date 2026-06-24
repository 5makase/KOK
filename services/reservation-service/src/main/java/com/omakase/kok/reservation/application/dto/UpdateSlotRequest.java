package com.omakase.kok.reservation.application.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@NoArgsConstructor
public class UpdateSlotRequest {

    private LocalDate slotDate;
    private LocalTime slotTime;

    @Min(1)
    private Integer maxCapacity;

    private Boolean depositRequired;
    private Long depositAmount;

    @AssertTrue(message = "예약금이 필요한 슬롯은 depositAmount가 0보다 커야 합니다.")
    public boolean isDepositAmountValid() {
        if (depositRequired == null || !depositRequired) {
            return true;
        }
        return depositAmount != null && depositAmount > 0;
    }
}
