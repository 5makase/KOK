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
public class WaitingCancelResponse {
    private UUID waitingId;
    private Long waitingNumber;
    private WaitingStatus status;
    private String cancelReason;
    private LocalDateTime cancelledAt;

    public static WaitingCancelResponse from(Waiting waiting) {
        return new WaitingCancelResponse(
                waiting.getId(),
                waiting.getWaitingNumber(),
                waiting.getStatus(),
                waiting.getCancelReason(),
                waiting.getCancelledAt()
        );
    }
}
