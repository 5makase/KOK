package com.omakase.kok.waiting.presentation.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitingSettingInitializeRequest {
    private Boolean waitingEnabled;

    @Positive(message = "최대 웨이팅 가능 팀 수는 1 이상이어야 합니다.")
    private Integer maxWaitingCount;

    @Positive(message = "호출 제한 시간은 1분 이상이어야 합니다.")
    private Integer callTimeoutMinutes;

    private Boolean allowUserCancel;

    @PositiveOrZero(message = "평균 대기 시간은 0분 이상이어야 합니다.")
    private Integer averageWaitingMinutes;
}
