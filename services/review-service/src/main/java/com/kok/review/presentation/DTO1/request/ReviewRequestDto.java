package com.kok.review.presentation.DTO1.request;

import jakarta.validation.constraints.*;
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
    @Size(min = 10, max = 1000, message = "리뷰는 10자 이상 1000자 이하로 작성하시오.")
    private String content;

    @Size(max = 5, message = "이미지는 최대 5장까지 첨부할 수 있습니다.")
    private List<
                    @NotBlank(message = "이미지 URL은 비어 있을 수 없습니다.")
                    @Size(max = 500, message = "이미지 URL은 500자를 초과할 수 없습니다.")
                    String> imageUrls;
}