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
    private String userName;
    private Long waitingNumber;
    private Integer peopleCount;
    private WaitingStatus status;
    private LocalDateTime calledAt;

    public static WaitingCallResponse of(Waiting waiting, String userName) {
        return new WaitingCallResponse(
                waiting.getId(),
                waiting.getStoreId(),
                waiting.getUserId(),
                userName,
                waiting.getWaitingNumber(),
                waiting.getPeopleCount(),
                waiting.getStatus(),
                waiting.getCalledAt()
        );
    }
}
