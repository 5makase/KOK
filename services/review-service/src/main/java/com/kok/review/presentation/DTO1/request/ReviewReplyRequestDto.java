package com.kok.review.presentation.DTO1.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReviewReplyRequestDto {

    @NotBlank(message = "답글 내용을 입력하시오.")
    @Size(min = 1, max = 1000, message = "답글은 1자 이상 1000자 이하로 작성하시오.")
    private String content;
}
