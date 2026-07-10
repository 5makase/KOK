package com.omakase.kok.waiting.presentation.dto.response;

import com.omakase.kok.waiting.domain.entity.WaitingSetting;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WaitingSettingInitializeResponse {
    private UUID storeId;
    private UUID waitingSettingId;
    private Boolean settingCreated;

    public static WaitingSettingInitializeResponse of(WaitingSetting setting, Boolean settingCreated) {
        return new WaitingSettingInitializeResponse(
                setting.getStoreId(),
                setting.getId(),
                settingCreated
        );
    }
}
