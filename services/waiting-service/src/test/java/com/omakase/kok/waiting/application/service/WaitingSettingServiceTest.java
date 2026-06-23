package com.omakase.kok.waiting.application.service;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.common.exception.CommonErrorCode;
import com.omakase.kok.waiting.domain.entity.WaitingSetting;
import com.omakase.kok.waiting.domain.repository.WaitingSettingRepository;
import com.omakase.kok.waiting.infrastructure.client.StoreFeignClient;
import com.omakase.kok.waiting.infrastructure.client.dto.StoreSummaryResponse;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore.StoreWaitingValues;
import com.omakase.kok.waiting.presentation.dto.request.WaitingSettingInitializeRequest;
import com.omakase.kok.waiting.presentation.dto.request.WaitingSettingUpdateRequest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Mock
    private StoreFeignClient storeFeignClient;

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
    @DisplayName("웨이팅 설정 수정은 본인 매장이면 요청값만 반영하고 Redis 캐시를 갱신한다")
    void updateWaitingSetting_success() {
        UUID storeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        WaitingSetting setting = WaitingSetting.create(storeId, true, 20, 8, true, 11);
        WaitingSettingUpdateRequest request = waitingSettingUpdateRequest(false, 50, null, false, 18);

        given(storeFeignClient.getStoreSummary(storeId))
                .willReturn(ApiResponse.success(StoreSummaryResponse.of(storeId, "테스트 매장", ownerId)));
        given(waitingSettingRepository.findByStoreId(storeId)).willReturn(Optional.of(setting));

        WaitingSettingResponse response = waitingSettingService.updateWaitingSetting(ownerId, "OWNER", storeId, request);

        assertThat(response.getStoreId()).isEqualTo(storeId);
        assertThat(response.getWaitingEnabled()).isFalse();
        assertThat(response.getMaxWaitingCount()).isEqualTo(50);
        assertThat(response.getCallTimeoutMinutes()).isEqualTo(8);
        assertThat(response.getAllowUserCancel()).isFalse();
        assertThat(response.getAverageWaitingMinutes()).isEqualTo(18);

        then(waitingQueueRedisStore).should()
                .cacheStoreValues(storeId, false, 50, 8, false, 18);
    }

    @Test
    @DisplayName("웨이팅 설정 수정은 본인 매장이 아니면 거부된다")
    void updateWaitingSetting_forbiddenWhenNotOwner() {
        UUID storeId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        given(storeFeignClient.getStoreSummary(storeId))
                .willReturn(ApiResponse.success(StoreSummaryResponse.of(storeId, "테스트 매장", UUID.randomUUID())));

        assertThatThrownBy(() -> waitingSettingService.updateWaitingSetting(
                requesterId,
                "OWNER",
                storeId,
                waitingSettingUpdateRequest(true, null, null, null, null)
        ))
                .isInstanceOf(BaseException.class)
                .satisfies(exception -> assertThat(((BaseException) exception).getErrorCode())
                        .isEqualTo(CommonErrorCode.ACCESS_DENIED));

        then(waitingSettingRepository).should(never()).findByStoreId(any(UUID.class));
        then(waitingQueueRedisStore).should(never())
                .cacheStoreValues(any(UUID.class), any(), any(), any(), any(), any());
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

    private WaitingSettingUpdateRequest waitingSettingUpdateRequest(
            Boolean waitingEnabled,
            Integer maxWaitingCount,
            Integer callTimeoutMinutes,
            Boolean allowUserCancel,
            Integer averageWaitingMinutes
    ) {
        WaitingSettingUpdateRequest request = newInstance(WaitingSettingUpdateRequest.class);
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
