package com.omakase.kok.waiting.presentation.dto.response;

import com.omakase.kok.waiting.domain.entity.Waiting;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NearTurnWaitingResponse {
    private UUID waitingId;
    private UUID userId;
    private Long currentRank;

    public static NearTurnWaitingResponse of(Waiting waiting, Long currentRank) {
        return new NearTurnWaitingResponse(
                waiting.getId(),
                waiting.getUserId(),
                currentRank
        );
    }
}
