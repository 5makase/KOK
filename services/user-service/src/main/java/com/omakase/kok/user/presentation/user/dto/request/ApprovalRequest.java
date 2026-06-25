package com.omakase.kok.user.presentation.user.dto.request;

import jakarta.validation.constraints.Size;

public record ApprovalRequest(
        @Size(max = 200, message = "거절 사유는 200자 이하로 입력해주세요.")
        String rejectReason
) {}