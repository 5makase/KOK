package com.omakase.kok.waiting.global.exception;

import com.omakase.kok.common.exception.BaseException;

public class WaitingException extends BaseException {
    public WaitingException(WaitingErrorCode errorCode) {
        super(errorCode);
    }
    public WaitingException(WaitingErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
