package com.omakase.kok.waiting.domain.entity;

import com.omakase.kok.common.entity.BaseEntity;
import com.omakase.kok.waiting.global.exception.WaitingErrorCode;
import com.omakase.kok.waiting.global.exception.WaitingException;
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
@Check(constraints = "max_waiting_count > 0 and call_timeout_minutes > 0 and average_waiting_minutes >= 0")
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

    @Column(name = "average_waiting_minutes", nullable = false)
    private Integer averageWaitingMinutes;

    @Builder(access = AccessLevel.PRIVATE)
    private WaitingSetting(UUID storeId, Boolean waitingEnabled, Integer maxWaitingCount,
                           Integer callTimeoutMinutes, Boolean allowUserCancel, Integer averageWaitingMinutes) {
        this.storeId = storeId;
        this.waitingEnabled = waitingEnabled;
        this.maxWaitingCount = maxWaitingCount;
        this.callTimeoutMinutes = callTimeoutMinutes;
        this.allowUserCancel = allowUserCancel;
        this.averageWaitingMinutes = averageWaitingMinutes;
    }

    public static WaitingSetting create(UUID storeId, Boolean waitingEnabled, Integer maxWaitingCount,
                                        Integer callTimeoutMinutes, Boolean allowUserCancel, Integer averageWaitingMinutes) {
        Integer defaultedMaxWaitingCount = maxWaitingCount != null ? maxWaitingCount : 100;
        Integer defaultedCallTimeoutMinutes = callTimeoutMinutes != null ? callTimeoutMinutes : 10;
        Integer defaultedAverageWaitingMinutes = averageWaitingMinutes != null ? averageWaitingMinutes : 10;
        validateValues(defaultedMaxWaitingCount, defaultedCallTimeoutMinutes, defaultedAverageWaitingMinutes);

        return WaitingSetting.builder()
                .storeId(storeId)
                .waitingEnabled(waitingEnabled != null ? waitingEnabled : false)
                .maxWaitingCount(defaultedMaxWaitingCount)
                .callTimeoutMinutes(defaultedCallTimeoutMinutes)
                .allowUserCancel(allowUserCancel != null ? allowUserCancel : true)
                .averageWaitingMinutes(defaultedAverageWaitingMinutes)
                .build();
    }

    public void update(Boolean waitingEnabled, Integer maxWaitingCount, Integer callTimeoutMinutes,
                       Boolean allowUserCancel, Integer averageWaitingMinutes) {
        validateValues(maxWaitingCount, callTimeoutMinutes, averageWaitingMinutes);
        if (waitingEnabled != null) {
            this.waitingEnabled = waitingEnabled;
        }
        if (maxWaitingCount != null) {
            this.maxWaitingCount = maxWaitingCount;
        }
        if (callTimeoutMinutes != null) {
            this.callTimeoutMinutes = callTimeoutMinutes;
        }
        if (allowUserCancel != null) {
            this.allowUserCancel = allowUserCancel;
        }
        if (averageWaitingMinutes != null) {
            this.averageWaitingMinutes = averageWaitingMinutes;
        }
    }

    private static void validateValues(Integer maxWaitingCount, Integer callTimeoutMinutes, Integer averageWaitingMinutes) {
        if ((maxWaitingCount != null && maxWaitingCount <= 0)
                || (callTimeoutMinutes != null && callTimeoutMinutes <= 0)
                || (averageWaitingMinutes != null && averageWaitingMinutes < 0)) {
            throw new WaitingException(WaitingErrorCode.WAITING_SETTING_INVALID);
        }
    }
}
