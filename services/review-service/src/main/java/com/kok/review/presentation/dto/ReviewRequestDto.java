package com.kok.review.presentation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequestDto {
    @NotNull(message = "매장을 지정하시오.")
    private UUID storeId;

    @NotNull(message = "예약을 지정하시요.")
    private UUID reservationId;

    @NotNull(message = "별점을 입력하시오.")
    @DecimalMin(value = "1.0", message = "별점은 최소 1.0입니다.")
    @DecimalMax(value = "5.0", message = "별점은 최대 5.0입니다.")
    private BigDecimal rating;

    @NotBlank(message = "리뷰를 작성하시오.")
    private String content;

    private List<UUID> imagesIds;
}
