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
public class WaitingNoShowResponse {
    private UUID waitingId;
    private WaitingStatus status;
    private String reason;
    private LocalDateTime noShowAt;

    public static WaitingNoShowResponse from(Waiting waiting) {
        return new WaitingNoShowResponse(
                waiting.getId(),
                waiting.getStatus(),
                waiting.getNoShowReason(),
                waiting.getNoShowedAt()
        );
    }
}
