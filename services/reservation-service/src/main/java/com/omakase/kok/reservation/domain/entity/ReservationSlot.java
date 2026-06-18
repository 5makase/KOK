package com.omakase.kok.reservation.domain.entity;

import com.omakase.kok.common.entity.BaseEntity;
import com.omakase.kok.reservation.domain.enums.SlotStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_reservation_slots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationSlot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "slot_id", updatable = false, nullable = false)
    private UUID slotId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "slot_time", nullable = false)
    private LocalTime slotTime;

    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity;

    @Column(name = "remaining_capacity", nullable = false)
    private int remainingCapacity;

    @Column(name = "deposit_required", nullable = false)
    private boolean depositRequired;

    @Column(name = "deposit_amount")
    private Long depositAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SlotStatus status;

    @Builder
    public ReservationSlot(UUID storeId, LocalDate slotDate, LocalTime slotTime,
                           int maxCapacity, boolean depositRequired, Long depositAmount) {
        if (depositRequired && (depositAmount == null || depositAmount <= 0)) {
            throw new IllegalArgumentException("예약금이 필요한 슬롯은 depositAmount가 0보다 커야 합니다.");
        }
        this.storeId = storeId;
        this.slotDate = slotDate;
        this.slotTime = slotTime;
        this.maxCapacity = maxCapacity;
        this.remainingCapacity = maxCapacity;
        this.depositRequired = depositRequired;
        this.depositAmount = depositAmount;
        this.status = SlotStatus.OPEN;
    }

    public void close() {
        this.status = SlotStatus.CLOSED;
    }

    public void cancel() {
        this.status = SlotStatus.CANCELLED;
    }

    public void decreaseCapacity(int size) {
        this.remainingCapacity -= size;
        if (this.remainingCapacity <= 0) {
            this.status = SlotStatus.FULL;
        }
    }

    public void increaseCapacity(int size) {
        this.remainingCapacity += size;
        if (this.remainingCapacity > 0 && this.status == SlotStatus.FULL) {
            this.status = SlotStatus.OPEN;
        }
    }
}
