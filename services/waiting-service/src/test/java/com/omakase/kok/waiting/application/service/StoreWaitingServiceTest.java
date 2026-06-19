package com.omakase.kok.waiting.application.service;

import com.omakase.kok.waiting.domain.entity.WaitingSetting;
import com.omakase.kok.waiting.domain.entity.WaitingSummary;
import com.omakase.kok.waiting.domain.repository.WaitingSettingRepository;
import com.omakase.kok.waiting.domain.repository.WaitingSummaryRepository;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore.StoreWaitingValues;
import com.omakase.kok.waiting.presentation.dto.request.WaitingSettingInitializeRequest;
import com.omakase.kok.waiting.presentation.dto.response.WaitingSettingInitializeResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingSummaryResponse;
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
class StoreWaitingServiceTest {
    @Mock
    private WaitingSettingRepository waitingSettingRepository;

    @Mock
    private WaitingSummaryRepository waitingSummaryRepository;

    @Mock
    private WaitingQueueRedisStore waitingQueueRedisStore;

    @InjectMocks
    private StoreWaitingService storeWaitingService;

    @Test
    @DisplayName("매장 웨이팅 요약은 기준값과 Redis 현재 대기 팀 수로 응답한다")
    void getWaitingSummary_success() {
        UUID storeId = UUID.randomUUID();

        given(waitingQueueRedisStore.getStoreValues(storeId))
                .willReturn(Optional.of(new StoreWaitingValues(true, 10, 5, true, 12)));
        given(waitingQueueRedisStore.count(storeId)).willReturn(3L);

        WaitingSummaryResponse response = storeWaitingService.getWaitingSummary(storeId);

        assertThat(response.getStoreId()).isEqualTo(storeId);
        assertThat(response.getWaitingAvailable()).isTrue();
        assertThat(response.getCurrentWaitingCount()).isEqualTo(3);
        assertThat(response.getAverageWaitingMinutes()).isEqualTo(12);
    }

    @Test
    @DisplayName("웨이팅 설정 초기화 시 기존 설정이 없으면 요청값으로 생성하고 Redis에 캐싱한다")
    void initializeWaitingSetting_createNewSettingAndSummary() {
        UUID storeId = UUID.randomUUID();
        WaitingSettingInitializeRequest request = waitingSettingInitializeRequest(true, 30, 5, false, 12);

        given(waitingSettingRepository.findByStoreId(storeId)).willReturn(Optional.empty());
        given(waitingSummaryRepository.findByStoreId(storeId)).willReturn(Optional.empty());
        given(waitingSettingRepository.save(any(WaitingSetting.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(waitingSummaryRepository.save(any(WaitingSummary.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        WaitingSettingInitializeResponse response = storeWaitingService.initializeWaitingSetting(storeId, request);

        assertThat(response.getStoreId()).isEqualTo(storeId);
        assertThat(response.getSettingCreated()).isTrue();
        assertThat(response.getSummaryCreated()).isTrue();

        ArgumentCaptor<WaitingSetting> settingCaptor = ArgumentCaptor.forClass(WaitingSetting.class);
        ArgumentCaptor<WaitingSummary> summaryCaptor = ArgumentCaptor.forClass(WaitingSummary.class);
        then(waitingSettingRepository).should().save(settingCaptor.capture());
        then(waitingSummaryRepository).should().save(summaryCaptor.capture());

        WaitingSetting setting = settingCaptor.getValue();
        assertThat(setting.getStoreId()).isEqualTo(storeId);
        assertThat(setting.getWaitingEnabled()).isTrue();
        assertThat(setting.getMaxWaitingCount()).isEqualTo(30);
        assertThat(setting.getCallTimeoutMinutes()).isEqualTo(5);
        assertThat(setting.getAllowUserCancel()).isFalse();

        WaitingSummary summary = summaryCaptor.getValue();
        assertThat(summary.getStoreId()).isEqualTo(storeId);
        assertThat(summary.getCurrentWaitingCount()).isZero();
        assertThat(summary.getAverageWaitingMinutes()).isEqualTo(12);
        assertThat(summary.getLastWaitingNumber()).isZero();

        then(waitingQueueRedisStore).should()
                .cacheStoreValues(storeId, true, 30, 5, false, 12);
    }

    @Test
    @DisplayName("웨이팅 설정 초기화 시 기존 설정이 있으면 새로 만들지 않고 기존값을 Redis에 캐싱한다")
    void initializeWaitingSetting_useExistingSettingAndSummary() {
        UUID storeId = UUID.randomUUID();
        WaitingSetting existingSetting = WaitingSetting.create(storeId, true, 20, 8, true);
        WaitingSummary existingSummary = WaitingSummary.builder()
                .storeId(storeId)
                .currentWaitingCount(4)
                .averageWaitingMinutes(11)
                .lastWaitingNumber(9L)
                .build();

        given(waitingSettingRepository.findByStoreId(storeId)).willReturn(Optional.of(existingSetting));
        given(waitingSummaryRepository.findByStoreId(storeId)).willReturn(Optional.of(existingSummary));

        WaitingSettingInitializeResponse response = storeWaitingService.initializeWaitingSetting(
                storeId,
                waitingSettingInitializeRequest(false, 100, 10, false, 30)
        );

        assertThat(response.getStoreId()).isEqualTo(storeId);
        assertThat(response.getSettingCreated()).isFalse();
        assertThat(response.getSummaryCreated()).isFalse();

        then(waitingSettingRepository).should(never()).save(any());
        then(waitingSummaryRepository).should(never()).save(any());
        then(waitingQueueRedisStore).should()
                .cacheStoreValues(storeId, true, 20, 8, true, 11);
    }

    @Test
    @DisplayName("웨이팅 기준값 캐시가 없으면 DB에서 조회한 뒤 Redis에 캐싱한다")
    void getStoreWaitingValues_cacheMiss() {
        UUID storeId = UUID.randomUUID();
        WaitingSetting setting = WaitingSetting.create(storeId, true, 40, 7, false);
        WaitingSummary summary = WaitingSummary.builder()
                .storeId(storeId)
                .currentWaitingCount(0)
                .averageWaitingMinutes(13)
                .lastWaitingNumber(0L)
                .build();

        given(waitingQueueRedisStore.getStoreValues(storeId)).willReturn(Optional.empty());
        given(waitingSettingRepository.findByStoreId(storeId)).willReturn(Optional.of(setting));
        given(waitingSummaryRepository.findByStoreId(storeId)).willReturn(Optional.of(summary));

        StoreWaitingValues values = storeWaitingService.getStoreWaitingValues(storeId);

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
