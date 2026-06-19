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
public class StoreWaitingResponse {
    private UUID waitingId;
    private UUID userId;
    private String visitorName;
    private Long waitingNumber;
    private Integer peopleCount;
    private WaitingStatus status;
    private Long currentRank;
    private Long teamsAhead;
    private String requestMessage;
    private LocalDateTime calledAt;
    private LocalDateTime createdAt;

    public static StoreWaitingResponse of(Waiting waiting, Long currentRank) {
        return new StoreWaitingResponse(
                waiting.getId(),
                waiting.getUserId(),
                waiting.getVisitorName(),
                waiting.getWaitingNumber(),
                waiting.getPeopleCount(),
                waiting.getStatus(),
                currentRank,
                calculateTeamsAhead(currentRank),
                waiting.getRequestMessage(),
                waiting.getCalledAt(),
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
