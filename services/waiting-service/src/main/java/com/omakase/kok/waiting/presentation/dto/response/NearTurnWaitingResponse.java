package com.omakase.kok.waiting.presentation.dto.response;

import com.omakase.kok.waiting.domain.entity.Waiting;
import com.omakase.kok.waiting.domain.enums.WaitingStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NearTurnWaitingResponse {
    private UUID waitingId;
    private UUID storeId;
    private UUID userId;
    private Long waitingNumber;
    private Integer peopleCount;
    private Long currentRank;
    private Long teamsAhead;
    private WaitingStatus status;

    public static NearTurnWaitingResponse of(Waiting waiting, Long currentRank) {
        return new NearTurnWaitingResponse(
                waiting.getId(),
                waiting.getStoreId(),
                waiting.getUserId(),
                waiting.getWaitingNumber(),
                waiting.getPeopleCount(),
                currentRank,
                calculateTeamsAhead(currentRank),
                waiting.getStatus()
        );
    }

    private static Long calculateTeamsAhead(Long currentRank) {
        if (currentRank == null || currentRank <= 0) {
            return null;
        }
        return currentRank - 1;
    }
}
