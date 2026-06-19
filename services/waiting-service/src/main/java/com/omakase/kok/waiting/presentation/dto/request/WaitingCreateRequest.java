package com.omakase.kok.waiting.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitingCreateRequest {
    @NotNull(message = "매장 ID는 필수입니다.")
    private UUID storeId;

    @NotNull(message = "방문 인원 수는 필수입니다.")
    @Positive(message = "방문 인원 수는 1명 이상이어야 합니다.")
    private Integer peopleCount;

    @Size(max = 500, message = "요청 사항은 500자 이하로 입력해야 합니다.")
    private String requestMessage;
}
