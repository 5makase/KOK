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
public class WaitingCallResponse {
    private UUID waitingId;
    private UUID storeId;
    private UUID userId;
    private String visitorName;
    private Long waitingNumber;
    private Integer peopleCount;
    private WaitingStatus status;
    private LocalDateTime calledAt;

    public static WaitingCallResponse from(Waiting waiting) {
        return new WaitingCallResponse(
                waiting.getId(),
                waiting.getStoreId(),
                waiting.getUserId(),
                waiting.getVisitorName(),
                waiting.getWaitingNumber(),
                waiting.getPeopleCount(),
                waiting.getStatus(),
                waiting.getCalledAt()
        );
    }
}
