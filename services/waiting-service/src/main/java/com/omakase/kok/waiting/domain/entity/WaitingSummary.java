package com.omakase.kok.waiting.domain.entity;

import com.omakase.kok.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_waiting_summaries")
@Check(constraints = "current_waiting_count >= 0 and average_waiting_minutes >= 0 and last_waiting_number >= 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitingSummary extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "waiting_summary_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false, unique = true)
    private UUID storeId;

    @Column(name = "current_waiting_count", nullable = false)
    private Integer currentWaitingCount;

    @Column(name = "average_waiting_minutes", nullable = false)
    private Integer averageWaitingMinutes;

    @Column(name = "last_waiting_number", nullable = false)
    private Long lastWaitingNumber;

    @Builder
    private WaitingSummary(UUID storeId, Integer currentWaitingCount, Integer averageWaitingMinutes,
                           Long lastWaitingNumber) {
        this.storeId = storeId;
        this.currentWaitingCount = currentWaitingCount != null ? currentWaitingCount : 0;
        this.averageWaitingMinutes = averageWaitingMinutes != null ? averageWaitingMinutes : 0;
        this.lastWaitingNumber = lastWaitingNumber != null ? lastWaitingNumber : 0L;
    }

    public Long registerWaiting() {
        this.lastWaitingNumber += 1;
        this.currentWaitingCount += 1;
        return this.lastWaitingNumber;
    }

    public void decreaseWaitingCount() {
        if (this.currentWaitingCount > 0) {
            this.currentWaitingCount -= 1;
        }
    }

    public void updateAverageWaitingMinutes(Integer averageWaitingMinutes) {
        this.averageWaitingMinutes = averageWaitingMinutes;
    }
}
