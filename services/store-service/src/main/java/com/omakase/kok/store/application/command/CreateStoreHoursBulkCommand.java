package com.omakase.kok.store.application.command;

import lombok.Builder;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CreateStoreHoursBulkCommand {

    private UUID storeId;
    private UUID requesterId;
    private List<HoursEntry> hours; // 7일치 고정 - Request 레벨에서 @Size(7) 검증 완료

    @Getter
    @Builder
    public static class HoursEntry {

        private DayOfWeek dayOfWeek;
        private LocalTime openTime;
        private LocalTime closeTime;
        private LocalTime breakStartTime;
        private LocalTime breakEndTime;
        private boolean isDayOff;
    }
}
