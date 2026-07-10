package com.omakase.kok.store.presentation.dto.response;

import com.omakase.kok.store.application.result.StoreHoursResult;
import lombok.Builder;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Builder
public class StoreHoursResponse {

    private UUID hoursId;
    private DayOfWeek dayOfWeek;
    private LocalTime openTime;
    private LocalTime closeTime;
    private LocalTime breakStartTime;
    private LocalTime breakEndTime;
    private boolean isDayOff;

    public static StoreHoursResponse from(StoreHoursResult result) {
        return StoreHoursResponse.builder()
                .hoursId(result.getHoursId())
                .dayOfWeek(result.getDayOfWeek())
                .openTime(result.getOpenTime())
                .closeTime(result.getCloseTime())
                .breakStartTime(result.getBreakStartTime())
                .breakEndTime(result.getBreakEndTime())
                .isDayOff(result.isDayOff())
                .build();
    }
}
