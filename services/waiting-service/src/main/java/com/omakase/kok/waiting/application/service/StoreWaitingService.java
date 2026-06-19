package com.omakase.kok.waiting.application.service;

import com.omakase.kok.waiting.domain.entity.WaitingSetting;
import com.omakase.kok.waiting.domain.entity.WaitingSummary;
import com.omakase.kok.waiting.domain.repository.WaitingSettingRepository;
import com.omakase.kok.waiting.domain.repository.WaitingSummaryRepository;
import com.omakase.kok.waiting.global.exception.WaitingErrorCode;
import com.omakase.kok.waiting.global.exception.WaitingException;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore.StoreWaitingValues;
import com.omakase.kok.waiting.presentation.dto.request.WaitingSettingInitializeRequest;
import com.omakase.kok.waiting.presentation.dto.response.WaitingSettingInitializeResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreWaitingService {
    private final WaitingSettingRepository waitingSettingRepository;
    private final WaitingSummaryRepository waitingSummaryRepository;
    private final WaitingQueueRedisStore waitingQueueRedisStore;

    // 매장 웨이팅 요약 조회
    public WaitingSummaryResponse getWaitingSummary(UUID storeId) {
        StoreWaitingValues storeWaitingValues = getStoreWaitingValues(storeId);
        Long currentWaitingCount = waitingQueueRedisStore.count(storeId);
        Boolean waitingAvailable = Boolean.TRUE.equals(storeWaitingValues.waitingEnabled())
                && currentWaitingCount < storeWaitingValues.maxWaitingCount();

        return WaitingSummaryResponse.of(
                storeId,
                waitingAvailable,
                toIntWaitingCount(currentWaitingCount),
                storeWaitingValues.averageWaitingMinutes()
        );
    }

    // 웨이팅 세팅 초기화
    @Transactional
    public WaitingSettingInitializeResponse initializeWaitingSetting(
            UUID storeId,
            WaitingSettingInitializeRequest request
    ) {
        Optional<WaitingSetting> existingSetting = waitingSettingRepository.findByStoreId(storeId);
        Boolean settingCreated = existingSetting.isEmpty();
        WaitingSetting setting = existingSetting.orElseGet(() -> waitingSettingRepository.save(WaitingSetting.create(
                storeId,
                request.getWaitingEnabled(),
                request.getMaxWaitingCount(),
                request.getCallTimeoutMinutes(),
                request.getAllowUserCancel()
        )));

        WaitingSummaryInitialization summaryInitialization = initializeWaitingSummary(
                storeId,
                request.getAverageWaitingMinutes()
        );
        WaitingSummary summary = summaryInitialization.summary();

        cacheStoreWaitingValues(setting, summary);

        return WaitingSettingInitializeResponse.of(setting, summary, settingCreated, summaryInitialization.created());
    }

    // 매장 웨이팅 기준값 조회 - cache-aside 패턴
    public StoreWaitingValues getStoreWaitingValues(UUID storeId) {
        return waitingQueueRedisStore.getStoreValues(storeId)
                .orElseGet(() -> {
                    WaitingSetting setting = waitingSettingRepository.findByStoreId(storeId)
                            .orElseThrow(() -> new WaitingException(WaitingErrorCode.WAITING_SETTING_NOT_FOUND));
                    WaitingSummary summary = initializeWaitingSummary(storeId, null).summary();

                    cacheStoreWaitingValues(setting, summary);

                    return new StoreWaitingValues(
                            setting.getWaitingEnabled(),
                            setting.getMaxWaitingCount(),
                            setting.getCallTimeoutMinutes(),
                            setting.getAllowUserCancel(),
                            summary.getAverageWaitingMinutes()
                    );
                });
    }

    // 웨이팅 요약 초기화
    private WaitingSummaryInitialization initializeWaitingSummary(UUID storeId, Integer averageWaitingMinutes) {
        return waitingSummaryRepository.findByStoreId(storeId)
                .map(summary -> new WaitingSummaryInitialization(summary, false))
                .orElseGet(() -> new WaitingSummaryInitialization(
                        waitingSummaryRepository.save(WaitingSummary.initialize(storeId, averageWaitingMinutes)),
                        true
                ));
    }

    // 매장 웨이팅 기준값 Redis 캐싱
    private void cacheStoreWaitingValues(WaitingSetting setting, WaitingSummary summary) {
        waitingQueueRedisStore.cacheStoreValues(
                setting.getStoreId(),
                setting.getWaitingEnabled(),
                setting.getMaxWaitingCount(),
                setting.getCallTimeoutMinutes(),
                setting.getAllowUserCancel(),
                summary.getAverageWaitingMinutes()
        );
    }

    private record WaitingSummaryInitialization(
            WaitingSummary summary,
            Boolean created
    ) {
    }

    // Redis 대기 팀 수를 응답 DTO 범위에 맞게 변환
    private Integer toIntWaitingCount(Long currentWaitingCount) {
        if (currentWaitingCount == null || currentWaitingCount < 0 || currentWaitingCount > Integer.MAX_VALUE) {
            throw new WaitingException(WaitingErrorCode.WAITING_COUNT_INVALID);
        }
        return currentWaitingCount.intValue();
    }
}
