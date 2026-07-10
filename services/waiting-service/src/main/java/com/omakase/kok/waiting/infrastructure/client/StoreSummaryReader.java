package com.omakase.kok.waiting.infrastructure.client;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.waiting.global.exception.WaitingErrorCode;
import com.omakase.kok.waiting.global.exception.WaitingException;
import com.omakase.kok.waiting.infrastructure.client.dto.StoreSummaryResponse;
import com.omakase.kok.waiting.infrastructure.redis.StoreSummaryCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StoreSummaryReader {
    private final StoreFeignClient storeFeignClient;
    private final StoreSummaryCacheRepository storeSummaryCacheRepository;

    // 캐시 조회
    public StoreSummaryResponse getStoreSummary(UUID storeId) {
        return storeSummaryCacheRepository.get(storeId)
                .filter(summary -> isValidCachedSummary(storeId, summary))
                .orElseGet(() -> getStoreSummaryFromStoreService(storeId));
    }

    // 캐시 miss -> DB에서 조회 후 캐시 저장
    private StoreSummaryResponse getStoreSummaryFromStoreService(UUID storeId) {
        ApiResponse<StoreSummaryResponse> response = storeFeignClient.getStoreSummary(storeId);
        StoreSummaryResponse summary = response == null ? null : response.getData();
        StoreSummaryResponse validatedSummary = validate(storeId, summary);
        storeSummaryCacheRepository.set(storeId, validatedSummary);
        return validatedSummary;
    }

    private StoreSummaryResponse validate(UUID requestedStoreId, StoreSummaryResponse summary) {
        if (summary == null
                || summary.getStoreId() == null
                || summary.getStoreName() == null
                || summary.getOwnerId() == null
                || !requestedStoreId.equals(summary.getStoreId())) {
            throw new WaitingException(WaitingErrorCode.WAITING_STORE_SUMMARY_UNAVAILABLE);
        }
        return summary;
    }

    private boolean isValidCachedSummary(UUID requestedStoreId, StoreSummaryResponse summary) {
        if (summary == null
                || summary.getStoreId() == null
                || summary.getStoreName() == null
                || summary.getOwnerId() == null
                || !requestedStoreId.equals(summary.getStoreId())) {
            log.warn("매장 요약 캐시 데이터 불완전 - store-service 조회로 fallback. storeId={}", requestedStoreId);
            return false;
        }
        return true;
    }
}
