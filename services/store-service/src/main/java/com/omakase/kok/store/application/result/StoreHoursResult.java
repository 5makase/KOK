package com.omakase.kok.store.application.result;

import com.omakase.kok.store.domain.entity.StoreHours;
import lombok.Builder;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Builder
public class StoreHoursResult {

    private UUID hoursId;
    private DayOfWeek dayOfWeek;
    private LocalTime openTime;
    private LocalTime closeTime;
    private LocalTime breakStartTime;
    private LocalTime breakEndTime;
    private boolean isDayOff;

    public static StoreHoursResult from(StoreHours storeHours) {
        return StoreHoursResult.builder()
                .hoursId(storeHours.getHoursId())
                .dayOfWeek(storeHours.getDayOfWeek())
                .openTime(storeHours.getOpenTime())
                .closeTime(storeHours.getCloseTime())
                .breakStartTime(storeHours.getBreakStartTime())
                .breakEndTime(storeHours.getBreakEndTime())
                .isDayOff(storeHours.isDayOff())
                .build();
    }
}
