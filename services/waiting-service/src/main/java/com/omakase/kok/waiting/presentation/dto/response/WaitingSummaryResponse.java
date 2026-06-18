package com.omakase.kok.waiting.presentation.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WaitingSummaryResponse {
    private UUID storeId;
    private Boolean waitingAvailable;
    private Integer currentWaitingCount;
    private Integer averageWaitingMinutes;
    private Integer estimatedWaitingMinutes;

    public static WaitingSummaryResponse of(
            UUID storeId,
            Boolean waitingAvailable,
            Integer currentWaitingCount,
            Integer averageWaitingMinutes
    ) {
        Integer estimatedWaitingMinutes = currentWaitingCount * averageWaitingMinutes;
        return new WaitingSummaryResponse(
                storeId,
                waitingAvailable,
                currentWaitingCount,
                averageWaitingMinutes,
                estimatedWaitingMinutes
        );
    }
}
