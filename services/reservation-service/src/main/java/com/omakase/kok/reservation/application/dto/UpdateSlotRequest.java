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

    @AssertTrue(message = "예약금 설정이 올바르지 않습니다. depositRequired=true이면 depositAmount > 0, false이면 depositAmount는 null이어야 합니다.")
    public boolean isDepositAmountValid() {
        if (depositRequired == null) return true;
        if (depositRequired) {
            return depositAmount != null && depositAmount > 0;
        }
        return depositAmount == null;
    }
}
