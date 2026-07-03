package com.omakase.kok.reservation.domain.entity;

import com.omakase.kok.common.entity.BaseEntity;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.reservation.domain.enums.SlotStatus;
import com.omakase.kok.reservation.domain.exception.ReservationErrorCode;
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

    @Column(name = "store_name", length = 100)
    private String storeName;

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
    public ReservationSlot(UUID storeId, String storeName, LocalDate slotDate, LocalTime slotTime,
                           int maxCapacity, boolean depositRequired, Long depositAmount) {
        if (depositRequired && (depositAmount == null || depositAmount <= 0)) {
            throw new IllegalArgumentException("예약금이 필요한 슬롯은 depositAmount가 0보다 커야 합니다.");
        }
        this.storeId = storeId;
        this.storeName = storeName;
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

    // PAYMENT_PENDING은 remainingCapacity(CONFIRMED 기준)에 반영되어 있지 않으므로 차감해야
    // Redis 잔여 인원과 맞춰야 할 실제 가용 인원이 된다. 정원 복구/드리프트 감지가 공통으로 사용.
    public long effectiveRemainingCapacity(long pendingSize) {
        return Math.max(0, this.remainingCapacity - pendingSize);
    }

    public void decreaseCapacity(int size) {
        if (this.remainingCapacity - size < 0) {
            throw new BaseException(ReservationErrorCode.SLOT_CAPACITY_EXCEEDED);
        }
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

    public void update(LocalDate slotDate, LocalTime slotTime, Integer maxCapacity,
                       Boolean depositRequired, Long depositAmount) {
        if (slotDate != null) this.slotDate = slotDate;
        if (slotTime != null) this.slotTime = slotTime;
        if (maxCapacity != null) {
            int used = this.maxCapacity - this.remainingCapacity;
            this.remainingCapacity = maxCapacity - used;
            this.maxCapacity = maxCapacity;
            if (this.remainingCapacity <= 0) {
                this.status = SlotStatus.FULL;
            } else if (this.status == SlotStatus.FULL) {
                this.status = SlotStatus.OPEN;
            }
        }
        if (depositRequired != null) {
            this.depositRequired = depositRequired;
            if (!depositRequired) this.depositAmount = null;
        }
        if (depositAmount != null && this.depositRequired) this.depositAmount = depositAmount;
    }
}
