package com.omakase.kok.store.application.result;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.omakase.kok.store.domain.entity.StoreHours;
import lombok.Builder;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Builder
// JsonDeserialize: Builder만 있으면 Jackson이 기본 생성자 없이 역직렬화 불가. Redis 캐시 복원 시 필요
@JsonDeserialize(builder = StoreHoursResult.StoreHoursResultBuilder.class)
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
