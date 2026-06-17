package com.omakase.kok.waiting.domain.entity;

import com.omakase.kok.common.entity.BaseEntity;
import com.omakase.kok.waiting.domain.enums.WaitingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_waitings")
@Check(constraints = "people_count > 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Waiting extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "waiting_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "waiting_number", nullable = false)
    private Long waitingNumber;

    @Column(name = "people_count", nullable = false)
    private Integer peopleCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WaitingStatus status;

    @Column(name = "expected_waiting_minutes")
    private Integer expectedWaitingMinutes;

    @Column(name = "request_message", length = 500)
    private String requestMessage;

    @Column(name = "called_at")
    private LocalDateTime calledAt;

    @Column(name = "entered_at")
    private LocalDateTime enteredAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "no_showed_at")
    private LocalDateTime noShowedAt;

    @Column(name = "no_show_reason", length = 500)
    private String noShowReason;

    @Builder
    private Waiting(UUID storeId, UUID userId, Long waitingNumber, Integer peopleCount,
                    Integer expectedWaitingMinutes, String requestMessage) {
        this.storeId = storeId;
        this.userId = userId;
        this.waitingNumber = waitingNumber;
        this.peopleCount = peopleCount;
        this.expectedWaitingMinutes = expectedWaitingMinutes;
        this.requestMessage = requestMessage;
        this.status = WaitingStatus.WAITING;
    }

    public void call() {
        this.status = WaitingStatus.CALLED;
        this.calledAt = LocalDateTime.now();
    }

    public void enter() {
        this.status = WaitingStatus.ENTERED;
        this.enteredAt = LocalDateTime.now();
    }

    public void cancel(String cancelReason) {
        this.status = WaitingStatus.CANCELLED;
        this.cancelReason = cancelReason;
        this.cancelledAt = LocalDateTime.now();
    }

    public void noShow(String noShowReason) {
        this.status = WaitingStatus.NO_SHOW;
        this.noShowReason = noShowReason;
        this.noShowedAt = LocalDateTime.now();
    }
}
