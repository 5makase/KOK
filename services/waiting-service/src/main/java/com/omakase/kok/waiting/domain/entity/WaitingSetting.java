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
@Table(name = "p_waiting_settings")
@Check(constraints = "max_waiting_count > 0 and call_timeout_minutes > 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitingSetting extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "waiting_setting_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false, unique = true)
    private UUID storeId;

    @Column(name = "is_waiting_enabled", nullable = false)
    private Boolean waitingEnabled;

    @Column(name = "max_waiting_count", nullable = false)
    private Integer maxWaitingCount;

    @Column(name = "call_timeout_minutes", nullable = false)
    private Integer callTimeoutMinutes;

    @Column(name = "allow_user_cancel", nullable = false)
    private Boolean allowUserCancel;

    @Builder
    private WaitingSetting(UUID storeId, Boolean waitingEnabled, Integer maxWaitingCount,
                           Integer callTimeoutMinutes, Boolean allowUserCancel) {
        this.storeId = storeId;
        this.waitingEnabled = waitingEnabled != null ? waitingEnabled : true;
        this.maxWaitingCount = maxWaitingCount != null ? maxWaitingCount : 100;
        this.callTimeoutMinutes = callTimeoutMinutes != null ? callTimeoutMinutes : 10;
        this.allowUserCancel = allowUserCancel != null ? allowUserCancel : true;
    }

    public void update(Boolean waitingEnabled, Integer maxWaitingCount, Integer callTimeoutMinutes,
                       Boolean allowUserCancel) {
        this.waitingEnabled = waitingEnabled;
        this.maxWaitingCount = maxWaitingCount;
        this.callTimeoutMinutes = callTimeoutMinutes;
        this.allowUserCancel = allowUserCancel;
    }
}
