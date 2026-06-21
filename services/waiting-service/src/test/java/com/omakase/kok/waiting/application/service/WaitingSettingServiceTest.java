package com.omakase.kok.waiting.application.service;

import com.omakase.kok.waiting.domain.entity.WaitingSetting;
import com.omakase.kok.waiting.domain.repository.WaitingSettingRepository;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore.StoreWaitingValues;
import com.omakase.kok.waiting.presentation.dto.request.WaitingSettingInitializeRequest;
import com.omakase.kok.waiting.presentation.dto.response.WaitingSettingInitializeResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingSettingResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WaitingSettingServiceTest {
    @Mock
    private WaitingSettingRepository waitingSettingRepository;

    @Mock
    private WaitingQueueRedisStore waitingQueueRedisStore;

    @InjectMocks
    private WaitingSettingService waitingSettingService;

    @Test
    @DisplayName("매장 웨이팅 설정 조회는 저장된 설정값을 응답한다")
    void getWaitingSetting_success() {
        UUID storeId = UUID.randomUUID();
        WaitingSetting setting = WaitingSetting.create(storeId, true, 10, 5, true, 12);

        given(waitingSettingRepository.findByStoreId(storeId)).willReturn(Optional.of(setting));

        WaitingSettingResponse response = waitingSettingService.getWaitingSetting(storeId);

        assertThat(response.getWaitingSettingId()).isEqualTo(setting.getId());
        assertThat(response.getStoreId()).isEqualTo(storeId);
        assertThat(response.getWaitingEnabled()).isTrue();
        assertThat(response.getMaxWaitingCount()).isEqualTo(10);
        assertThat(response.getCallTimeoutMinutes()).isEqualTo(5);
        assertThat(response.getAllowUserCancel()).isTrue();
        assertThat(response.getAverageWaitingMinutes()).isEqualTo(12);
    }

    @Test
    @DisplayName("웨이팅 설정 초기화 시 기존 설정이 없으면 요청값으로 생성하고 Redis에 캐싱한다")
    void initializeWaitingSetting_createNewSetting() {
        UUID storeId = UUID.randomUUID();
        WaitingSettingInitializeRequest request = waitingSettingInitializeRequest(true, 30, 5, false, 12);

        given(waitingSettingRepository.findByStoreId(storeId)).willReturn(Optional.empty());
        given(waitingSettingRepository.save(any(WaitingSetting.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        WaitingSettingInitializeResponse response = waitingSettingService.initializeWaitingSetting(storeId, request);

        assertThat(response.getStoreId()).isEqualTo(storeId);
        assertThat(response.getSettingCreated()).isTrue();

        ArgumentCaptor<WaitingSetting> settingCaptor = ArgumentCaptor.forClass(WaitingSetting.class);
        then(waitingSettingRepository).should().save(settingCaptor.capture());

        WaitingSetting setting = settingCaptor.getValue();
        assertThat(setting.getStoreId()).isEqualTo(storeId);
        assertThat(setting.getWaitingEnabled()).isTrue();
        assertThat(setting.getMaxWaitingCount()).isEqualTo(30);
        assertThat(setting.getCallTimeoutMinutes()).isEqualTo(5);
        assertThat(setting.getAllowUserCancel()).isFalse();
        assertThat(setting.getAverageWaitingMinutes()).isEqualTo(12);

        then(waitingQueueRedisStore).should()
                .cacheStoreValues(storeId, true, 30, 5, false, 12);
    }

    @Test
    @DisplayName("웨이팅 설정 초기화 시 기존 설정이 있으면 새로 만들지 않고 기존값을 Redis에 캐싱한다")
    void initializeWaitingSetting_useExistingSetting() {
        UUID storeId = UUID.randomUUID();
        WaitingSetting existingSetting = WaitingSetting.create(storeId, true, 20, 8, true, 11);

        given(waitingSettingRepository.findByStoreId(storeId)).willReturn(Optional.of(existingSetting));

        WaitingSettingInitializeResponse response = waitingSettingService.initializeWaitingSetting(
                storeId,
                waitingSettingInitializeRequest(false, 100, 10, false, 30)
        );

        assertThat(response.getStoreId()).isEqualTo(storeId);
        assertThat(response.getSettingCreated()).isFalse();

        then(waitingSettingRepository).should(never()).save(any());
        then(waitingQueueRedisStore).should()
                .cacheStoreValues(storeId, true, 20, 8, true, 11);
    }

    @Test
    @DisplayName("웨이팅 기준값 캐시가 없으면 DB에서 조회한 뒤 Redis에 캐싱한다")
    void getStoreWaitingValues_cacheMiss() {
        UUID storeId = UUID.randomUUID();
        WaitingSetting setting = WaitingSetting.create(storeId, true, 40, 7, false, 13);

        given(waitingQueueRedisStore.getStoreValues(storeId)).willReturn(Optional.empty());
        given(waitingSettingRepository.findByStoreId(storeId)).willReturn(Optional.of(setting));

        StoreWaitingValues values = waitingSettingService.getStoreWaitingValues(storeId);

        assertThat(values.waitingEnabled()).isTrue();
        assertThat(values.maxWaitingCount()).isEqualTo(40);
        assertThat(values.callTimeoutMinutes()).isEqualTo(7);
        assertThat(values.allowUserCancel()).isFalse();
        assertThat(values.averageWaitingMinutes()).isEqualTo(13);
        then(waitingQueueRedisStore).should()
                .cacheStoreValues(storeId, true, 40, 7, false, 13);
    }

    private WaitingSettingInitializeRequest waitingSettingInitializeRequest(
            Boolean waitingEnabled,
            Integer maxWaitingCount,
            Integer callTimeoutMinutes,
            Boolean allowUserCancel,
            Integer averageWaitingMinutes
    ) {
        WaitingSettingInitializeRequest request = newInstance(WaitingSettingInitializeRequest.class);
        ReflectionTestUtils.setField(request, "waitingEnabled", waitingEnabled);
        ReflectionTestUtils.setField(request, "maxWaitingCount", maxWaitingCount);
        ReflectionTestUtils.setField(request, "callTimeoutMinutes", callTimeoutMinutes);
        ReflectionTestUtils.setField(request, "allowUserCancel", allowUserCancel);
        ReflectionTestUtils.setField(request, "averageWaitingMinutes", averageWaitingMinutes);
        return request;
    }

    private <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create test request: " + type.getSimpleName(), e);
        }
    }
}
