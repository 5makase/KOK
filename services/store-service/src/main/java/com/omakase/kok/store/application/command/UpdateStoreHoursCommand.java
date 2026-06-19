package com.omakase.kok.store.application.command;

import com.omakase.kok.store.presentation.dto.request.UpdateStoreHoursRequest;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;
import java.util.UUID;

@Getter
@Builder
public class UpdateStoreHoursCommand {

    private UUID storeId;
    private UUID hoursId;
    private UUID requesterId;
    private LocalTime openTime;
    private LocalTime closeTime;
    private LocalTime breakStartTime;
    private LocalTime breakEndTime;
    private boolean isDayOff;

    public static UpdateStoreHoursCommand of(UUID storeId, UUID hoursId, UUID requesterId, UpdateStoreHoursRequest request) {
        return UpdateStoreHoursCommand.builder()
                .storeId(storeId)
                .hoursId(hoursId)
                .requesterId(requesterId)
                .openTime(request.getOpenTime())
                .closeTime(request.getCloseTime())
                .breakStartTime(request.getBreakStartTime())
                .breakEndTime(request.getBreakEndTime())
                .isDayOff(request.getIsDayOff())
                .build();
    }
}
