package com.kok.review.global.exception;

import com.omakase.kok.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

//Review에서 ErrorCode를 구현
@Getter
@RequiredArgsConstructor
public enum ReviewErrorCode implements ErrorCode {

    //매장 서비스에 연결이 되지 않았을 때
    STORE_SERVICE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,   // 503 에러
            "REVIEW-503",
    "매장 서비스가 일시적으로 응답하지 않아 답글 작성을 처리할 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
