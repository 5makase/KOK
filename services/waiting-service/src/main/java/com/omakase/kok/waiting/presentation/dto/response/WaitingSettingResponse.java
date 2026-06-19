package com.omakase.kok.waiting.presentation.dto.response;

import com.omakase.kok.waiting.domain.entity.WaitingSetting;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WaitingSettingResponse {
    private UUID waitingSettingId;
    private UUID storeId;
    private Boolean waitingEnabled;
    private Integer maxWaitingCount;
    private Integer callTimeoutMinutes;
    private Boolean allowUserCancel;
    private Integer averageWaitingMinutes;

    public static WaitingSettingResponse of(WaitingSetting setting) {
        return new WaitingSettingResponse(
                setting.getId(),
                setting.getStoreId(),
                setting.getWaitingEnabled(),
                setting.getMaxWaitingCount(),
                setting.getCallTimeoutMinutes(),
                setting.getAllowUserCancel(),
                setting.getAverageWaitingMinutes()
        );
    }
}
