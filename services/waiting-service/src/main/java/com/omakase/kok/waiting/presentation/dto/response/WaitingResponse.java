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
public class WaitingResponse {
    private UUID waitingId;
    private UUID storeId;
    private String storeName;
    private UUID userId;
    private Long waitingNumber;
    private Integer peopleCount;
    private WaitingStatus status;
    private Long currentRank;
    private Long teamsAhead;
    private LocalDateTime createdAt;

    public static WaitingResponse of(Waiting waiting, Long currentRank) {
        return new WaitingResponse(
                waiting.getId(),
                waiting.getStoreId(),
                waiting.getStoreName(),
                waiting.getUserId(),
                waiting.getWaitingNumber(),
                waiting.getPeopleCount(),
                waiting.getStatus(),
                currentRank,
                calculateTeamsAhead(currentRank),
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
