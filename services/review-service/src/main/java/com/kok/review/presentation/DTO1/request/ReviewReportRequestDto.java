package com.kok.review.presentation.DTO1.request;

import com.kok.review.domain.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReviewReportRequestDto {

    @NotNull(message = "신고 사유를 선택하시오.")
    private ReportReason reason;

    @Size(max = 500, message = "상세 사유는 500자를 초과할 수 없습니다.")
    private String detail;   // ETC일 때 필수 (서비스에서 검증)
}
