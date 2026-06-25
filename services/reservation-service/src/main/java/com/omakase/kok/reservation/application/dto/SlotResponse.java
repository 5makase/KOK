package com.omakase.kok.reservation.application.dto;

import com.omakase.kok.reservation.domain.entity.ReservationSlot;
import com.omakase.kok.reservation.domain.enums.SlotStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Builder
public class SlotResponse {

    private UUID slotId;
    private UUID storeId;
    private String storeName;
    private LocalDate slotDate;
    private LocalTime slotTime;
    private int maxCapacity;
    private int remainingCapacity;
    private boolean depositRequired;
    private Long depositAmount;
    private SlotStatus status;
    private LocalDateTime createdAt;

    public static SlotResponse from(ReservationSlot slot) {
        return SlotResponse.builder()
                .slotId(slot.getSlotId())
                .storeId(slot.getStoreId())
                .storeName(slot.getStoreName())
                .slotDate(slot.getSlotDate())
                .slotTime(slot.getSlotTime())
                .maxCapacity(slot.getMaxCapacity())
                .remainingCapacity(slot.getRemainingCapacity())
                .depositRequired(slot.isDepositRequired())
                .depositAmount(slot.getDepositAmount())
                .status(slot.getStatus())
                .createdAt(slot.getCreatedAt())
                .build();
    }
}
