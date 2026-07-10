package com.kok.review.infrastructure.client;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class StoreClientFallbackFactory implements FallbackFactory<StoreClient> {

    @Override
    public StoreClient create(Throwable cause) {
        return storeId -> {
            log.warn("store-service 호출 실패 (storeId={})", storeId, cause);
            throw new StoreServiceUnavailableException();
        };
    }
}
