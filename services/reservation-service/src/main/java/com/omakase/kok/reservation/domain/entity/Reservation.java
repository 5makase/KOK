package com.omakase.kok.reservation.domain.entity;

import com.omakase.kok.common.entity.BaseEntity;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.reservation.domain.exception.ReservationErrorCode;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_reservations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "reservation_id", updatable = false, nullable = false)
    private UUID reservationId;

    @Column(name = "slot_id", nullable = false)
    private UUID slotId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "store_name", length = 100)
    private String storeName;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "booker_name", nullable = false, length = 50)
    private String bookerName;

    @Column(name = "booker_phone", nullable = false, length = 20)
    private String bookerPhone;

    @Column(name = "reservation_size", nullable = false)
    private int reservationSize;

    @Column(name = "request_message", length = 200)
    private String requestMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "visited_at")
    private LocalDateTime visitedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by", length = 100)
    private String cancelledBy;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    @Builder
    public Reservation(UUID slotId, UUID userId, UUID storeId, String storeName, LocalDateTime scheduledAt,
                       String bookerName, String bookerPhone, int reservationSize, String requestMessage) {
        this.slotId = slotId;
        this.userId = userId;
        this.storeId = storeId;
        this.storeName = storeName;
        this.scheduledAt = scheduledAt;
        this.bookerName = bookerName;
        this.bookerPhone = bookerPhone;
        this.reservationSize = reservationSize;
        this.requestMessage = requestMessage;
        this.status = ReservationStatus.PAYMENT_PENDING;
    }

    public void confirm() {
        if (this.status != ReservationStatus.PAYMENT_PENDING) {
            throw new BaseException(ReservationErrorCode.RESERVATION_NOT_CONFIRMABLE);
        }
        this.status = ReservationStatus.CONFIRMED;
    }

    public void cancel(String cancelledBy, String cancelReason) {
        if (!isCancellable()) {
            throw new BaseException(ReservationErrorCode.RESERVATION_NOT_CANCELLABLE);
        }
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancelledBy = cancelledBy;
        this.cancelReason = cancelReason;
    }

    public void visit() {
        if (this.status != ReservationStatus.CONFIRMED) {
            throw new BaseException(ReservationErrorCode.RESERVATION_NOT_VISITABLE);
        }
        this.status = ReservationStatus.VISITED;
        this.visitedAt = LocalDateTime.now();
    }

    public void noShow() {
        if (this.status != ReservationStatus.CONFIRMED) {
            throw new BaseException(ReservationErrorCode.RESERVATION_NOT_VISITABLE);
        }
        this.status = ReservationStatus.NO_SHOW;
    }

    public void change(int reservationSize) {
        if (this.status != ReservationStatus.CONFIRMED) {
            throw new BaseException(ReservationErrorCode.RESERVATION_NOT_CHANGEABLE);
        }
        this.reservationSize = reservationSize;
    }

    public boolean isCancellable() {
        return this.status == ReservationStatus.PAYMENT_PENDING
                || this.status == ReservationStatus.CONFIRMED;
    }
}
