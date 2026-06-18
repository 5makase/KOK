package com.omakase.kok.store.application.command;

import com.omakase.kok.store.presentation.dto.request.CreateStoreHoursBulkRequest;
import com.omakase.kok.store.presentation.dto.request.CreateStoreHoursRequest;
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
    private List<HoursEntry> hours; // 7일치 고정 — Request 레벨에서 @Size(7) 검증 완료

    public static CreateStoreHoursBulkCommand of(UUID storeId, UUID requesterId, CreateStoreHoursBulkRequest request) {
        return CreateStoreHoursBulkCommand.builder()
                .storeId(storeId)
                .requesterId(requesterId)
                .hours(request.getHours().stream()
                        .map(HoursEntry::from)
                        .toList())
                .build();
    }

    // Request의 중첩 DTO를 Command 내부로 격리 — presentation 타입이 Service까지 전파되지 않도록
    @Getter
    @Builder
    public static class HoursEntry {

        private DayOfWeek dayOfWeek;
        private LocalTime openTime;
        private LocalTime closeTime;
        private LocalTime breakStartTime;
        private LocalTime breakEndTime;
        private boolean isDayOff;

        public static HoursEntry from(CreateStoreHoursRequest request) {
            return HoursEntry.builder()
                    .dayOfWeek(request.getDayOfWeek())
                    .openTime(request.getOpenTime())
                    .closeTime(request.getCloseTime())
                    .breakStartTime(request.getBreakStartTime())
                    .breakEndTime(request.getBreakEndTime())
                    .isDayOff(request.getIsDayOff())
                    .build();
        }
    }
}
