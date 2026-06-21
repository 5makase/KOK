package com.omakase.kok.waiting.application.service;

import com.omakase.kok.waiting.domain.entity.WaitingSetting;
import com.omakase.kok.waiting.domain.repository.WaitingSettingRepository;
import com.omakase.kok.waiting.global.exception.WaitingErrorCode;
import com.omakase.kok.waiting.global.exception.WaitingException;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore.StoreWaitingValues;
import com.omakase.kok.waiting.presentation.dto.request.WaitingSettingInitializeRequest;
import com.omakase.kok.waiting.presentation.dto.response.WaitingSettingInitializeResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingSettingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaitingSettingService {
    private final WaitingSettingRepository waitingSettingRepository;
    private final WaitingQueueRedisStore waitingQueueRedisStore;

    // 웨이팅 세팅 조회
    public WaitingSettingResponse getWaitingSetting(UUID storeId) {
        WaitingSetting setting = waitingSettingRepository.findByStoreId(storeId)
                .orElseThrow(() -> new WaitingException(WaitingErrorCode.WAITING_SETTING_NOT_FOUND));
        return WaitingSettingResponse.of(setting);
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
                request.getAllowUserCancel(),
                request.getAverageWaitingMinutes()
        )));
        cacheStoreWaitingValues(setting);
        return WaitingSettingInitializeResponse.of(setting, settingCreated);
    }

    // 매장 웨이팅 기준값 조회 - cache-aside 패턴
    public StoreWaitingValues getStoreWaitingValues(UUID storeId) {
        return waitingQueueRedisStore.getStoreValues(storeId)
                .orElseGet(() -> {
                    WaitingSetting setting = waitingSettingRepository.findByStoreId(storeId)
                            .orElseThrow(() -> new WaitingException(WaitingErrorCode.WAITING_SETTING_NOT_FOUND));
                    cacheStoreWaitingValues(setting);

                    return new StoreWaitingValues(
                            setting.getWaitingEnabled(),
                            setting.getMaxWaitingCount(),
                            setting.getCallTimeoutMinutes(),
                            setting.getAllowUserCancel(),
                            setting.getAverageWaitingMinutes()
                    );
                });
    }

    // 매장 웨이팅 기준값 Redis 캐싱
    private void cacheStoreWaitingValues(WaitingSetting setting) {
        waitingQueueRedisStore.cacheStoreValues(
                setting.getStoreId(),
                setting.getWaitingEnabled(),
                setting.getMaxWaitingCount(),
                setting.getCallTimeoutMinutes(),
                setting.getAllowUserCancel(),
                setting.getAverageWaitingMinutes()
        );
    }

}
