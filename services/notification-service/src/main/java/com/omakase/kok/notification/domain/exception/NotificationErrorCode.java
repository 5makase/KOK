package com.omakase.kok.notification.domain.exception;

import com.omakase.kok.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {

    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION-001", "알림을 찾을 수 없습니다."),
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "NOTIFICATION-002", "페이지 크기는 10, 30, 50만 허용됩니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
