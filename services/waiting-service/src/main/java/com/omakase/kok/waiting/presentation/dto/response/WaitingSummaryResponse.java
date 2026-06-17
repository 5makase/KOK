package com.omakase.kok.waiting.presentation.dto.response;

import com.omakase.kok.waiting.domain.entity.WaitingSummary;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WaitingSummaryResponse {
    private UUID storeId;
    private Boolean waitingAvailable;
    private Integer currentWaitingCount;
    private Integer averageTurnoverMinutes;
    private Integer estimatedWaitingMinutes;
    private LocalDateTime calculatedAt;

    public static WaitingSummaryResponse of(WaitingSummary summary, Boolean waitingAvailable) {
        Integer estimatedWaitingMinutes = summary.getCurrentWaitingCount() * summary.getAverageWaitingMinutes();
        return new WaitingSummaryResponse(
                summary.getStoreId(),
                waitingAvailable,
                summary.getCurrentWaitingCount(),
                summary.getAverageWaitingMinutes(),
                estimatedWaitingMinutes,
                LocalDateTime.now()
        );
    }
}
