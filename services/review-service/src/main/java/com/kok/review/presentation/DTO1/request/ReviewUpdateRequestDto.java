package com.kok.review.presentation.DTO1.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@NoArgsConstructor
public class ReviewUpdateRequestDto {

    @NotNull(message = "별점을 입력하시오.")
    @DecimalMin(value = "1.0", message = "별점은 최소 1.0입니다.")
    @DecimalMax(value = "5.0", message = "별점은 최대 5.0입니다.")
    private BigDecimal rating;

    @NotBlank(message = "리뷰를 작성하시오.")
    @Size(min = 10, max = 1000, message = "리뷰는 10자 이상 1000자 이하로 작성하시오.")
    private String content;

    //[원래 부터 없거나, 기존 이미지 URL을 보내거나, 새롭게 붙히거나] 이 셋 중 하나임.
    @Size(max = 5, message = "이미지는 최대 5장까지 첨부할 수 있습니다.")
    private List<
                @NotBlank(message = "이미지 URL은 비어 있을 수 없습니다.")
                @Size(max = 500, message = "이미지 URL은 500자를 초과할 수 없습니다.")
                String> imageUrls;
}
