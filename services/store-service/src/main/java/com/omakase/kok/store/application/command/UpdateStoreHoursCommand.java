package com.omakase.kok.store.application.command;

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
}
