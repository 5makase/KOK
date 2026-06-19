package com.omakase.kok.waiting.presentation.dto.response;

import com.omakase.kok.waiting.domain.entity.WaitingSetting;
import com.omakase.kok.waiting.domain.entity.WaitingSummary;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WaitingSettingInitializeResponse {
    private UUID storeId;
    private UUID waitingSettingId;
    private UUID waitingSummaryId;
    private Boolean settingCreated;
    private Boolean summaryCreated;

    public static WaitingSettingInitializeResponse of(
            WaitingSetting setting,
            WaitingSummary summary,
            Boolean settingCreated,
            Boolean summaryCreated
    ) {
        return new WaitingSettingInitializeResponse(
                setting.getStoreId(),
                setting.getId(),
                summary.getId(),
                settingCreated,
                summaryCreated
        );
    }
}
