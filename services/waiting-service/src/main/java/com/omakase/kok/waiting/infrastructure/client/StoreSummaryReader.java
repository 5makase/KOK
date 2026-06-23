package com.omakase.kok.waiting.infrastructure.client;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.waiting.global.exception.WaitingErrorCode;
import com.omakase.kok.waiting.global.exception.WaitingException;
import com.omakase.kok.waiting.infrastructure.client.dto.StoreSummaryResponse;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StoreSummaryReader {
    private final StoreFeignClient storeFeignClient;

    public StoreSummaryReader(StoreFeignClient storeFeignClient) {
        this.storeFeignClient = storeFeignClient;
    }

    public StoreSummaryResponse getStoreSummary(UUID storeId) {
        ApiResponse<StoreSummaryResponse> response = storeFeignClient.getStoreSummary(storeId);
        StoreSummaryResponse summary = response == null ? null : response.getData();
        if (summary == null
                || summary.getStoreId() == null
                || summary.getStoreName() == null
                || summary.getOwnerId() == null) {
            throw new WaitingException(WaitingErrorCode.WAITING_STORE_SUMMARY_UNAVAILABLE);
        }
        return summary;
    }
}
