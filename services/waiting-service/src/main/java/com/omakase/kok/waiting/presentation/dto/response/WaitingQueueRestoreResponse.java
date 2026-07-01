package com.omakase.kok.waiting.presentation.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WaitingQueueRestoreResponse {
    private UUID storeId;
    private LocalDate waitingDate;
    private Integer restoredCount;
    private Long maxWaitingNumber;
    private LocalDateTime restoredAt;

    public static WaitingQueueRestoreResponse of(
            UUID storeId,
            LocalDate waitingDate,
            Integer restoredCount,
            Long maxWaitingNumber
    ) {
        return new WaitingQueueRestoreResponse(
                storeId,
                waitingDate,
                restoredCount,
                maxWaitingNumber,
                LocalDateTime.now()
        );
    }
}
