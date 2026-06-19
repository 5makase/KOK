package com.omakase.kok.waiting.presentation.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitingCancelRequest {
    @Size(max = 500, message = "취소 사유는 500자 이하로 입력해야 합니다.")
    private String cancelReason;
}
