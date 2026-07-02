package com.kok.review.infrastructure.client;

import com.kok.review.global.exception.ReviewErrorCode;
import com.omakase.kok.common.exception.BaseException;

public class StoreServiceUnavailableException extends BaseException {
    public StoreServiceUnavailableException() {
        super(ReviewErrorCode.STORE_SERVICE_UNAVAILABLE);
    }
}
