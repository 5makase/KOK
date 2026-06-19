package com.omakase.kok.waiting.presentation.dto.response;

import com.omakase.kok.waiting.domain.entity.Waiting;
import com.omakase.kok.waiting.domain.enums.WaitingStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WaitingDetailResponse {
    private UUID waitingId;
    private UUID storeId;
    private String storeName;
    private UUID userId;
    private Long waitingNumber;
    private Integer peopleCount;
    private String requestMessage;
    private WaitingStatus status;
    private Long currentRank;
    private Long teamsAhead;
    private Integer expectedWaitingMinutes;
    private LocalDateTime calledAt;
    private LocalDateTime enteredAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime noShowAt;
    private LocalDateTime createdAt;

    public static WaitingDetailResponse of(Waiting waiting, Long currentRank) {
        return new WaitingDetailResponse(
                waiting.getId(),
                waiting.getStoreId(),
                waiting.getStoreName(),
                waiting.getUserId(),
                waiting.getWaitingNumber(),
                waiting.getPeopleCount(),
                waiting.getRequestMessage(),
                waiting.getStatus(),
                currentRank,
                calculateTeamsAhead(currentRank),
                waiting.getExpectedWaitingMinutes(),
                waiting.getCalledAt(),
                waiting.getEnteredAt(),
                waiting.getCancelledAt(),
                waiting.getNoShowedAt(),
                waiting.getCreatedAt()
        );
    }

    private static Long calculateTeamsAhead(Long currentRank) {
        if (currentRank == null || currentRank <= 0) {
            return null;
        }
        return currentRank - 1;
    }
}
