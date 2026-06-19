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
public class WaitingEnterResponse {
    private UUID waitingId;
    private WaitingStatus status;
    private LocalDateTime enteredAt;

    public static WaitingEnterResponse from(Waiting waiting) {
        return new WaitingEnterResponse(
                waiting.getId(),
                waiting.getStatus(),
                waiting.getEnteredAt()
        );
    }
}
