package com.omakase.kok.notification.domain.exception;

import com.omakase.kok.common.exception.BaseException;

public class NotificationException extends BaseException {

    public NotificationException(NotificationErrorCode errorCode) {
        super(errorCode);
    }
}
