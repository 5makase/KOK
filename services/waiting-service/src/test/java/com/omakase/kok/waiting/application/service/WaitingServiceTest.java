package com.omakase.kok.waiting.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.waiting.domain.entity.Waiting;
import com.omakase.kok.waiting.domain.entity.WaitingSetting;
import com.omakase.kok.waiting.domain.entity.WaitingSummary;
import com.omakase.kok.waiting.domain.enums.WaitingStatus;
import com.omakase.kok.waiting.domain.repository.WaitingOutboxEventRepository;
import com.omakase.kok.waiting.domain.repository.WaitingRepository;
import com.omakase.kok.waiting.domain.repository.WaitingSettingRepository;
import com.omakase.kok.waiting.domain.repository.WaitingSummaryRepository;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore.StoreWaitingValues;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore.WaitingRegistration;
import com.omakase.kok.waiting.presentation.dto.request.WaitingCreateRequest;
import com.omakase.kok.waiting.presentation.dto.request.WaitingSettingInitializeRequest;
import com.omakase.kok.waiting.presentation.dto.response.WaitingResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingSettingInitializeResponse;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WaitingServiceTest {
    @Mock
    private WaitingRepository waitingRepository;

    @Mock
    private WaitingSettingRepository waitingSettingRepository;

    @Mock
    private WaitingSummaryRepository waitingSummaryRepository;

    @Mock
    private WaitingOutboxEventRepository waitingOutboxEventRepository;

    @Mock
    private WaitingQueueRedisStore waitingQueueRedisStore;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private WaitingService waitingService;

    @Test
    @DisplayName("웨이팅을 등록하면 Redis 순번 기준으로 웨이팅을 저장하고 응답한다")
    void createWaiting_success() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WaitingCreateRequest request = waitingCreateRequest(storeId, 3, "창가 자리 부탁드립니다.");

        given(waitingQueueRedisStore.getStoreValues(storeId))
                .willReturn(Optional.of(new StoreWaitingValues(true, 50, 10, true, 15)));
        given(waitingQueueRedisStore.register(eq(storeId), eq(userId), any(UUID.class), eq(50)))
                .willReturn(Optional.of(new WaitingRegistration(7L, 3L)));
        given(waitingRepository.saveAndFlush(any(Waiting.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        WaitingResponse response = waitingService.createWaiting(userId, request);

        assertThat(response.getStoreId()).isEqualTo(storeId);
        assertThat(response.getStoreName()).isEqualTo("UNKNOWN");
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getWaitingNumber()).isEqualTo(7L);
        assertThat(response.getPeopleCount()).isEqualTo(3);
        assertThat(response.getStatus()).isEqualTo(WaitingStatus.WAITING);
        assertThat(response.getCurrentRank()).isEqualTo(3L);
        assertThat(response.getTeamsAhead()).isEqualTo(2L);
        assertThat(response.getExpectedWaitingMinutes()).isEqualTo(30);

        ArgumentCaptor<Waiting> waitingCaptor = ArgumentCaptor.forClass(Waiting.class);
        then(waitingRepository).should().saveAndFlush(waitingCaptor.capture());
        Waiting savedWaiting = waitingCaptor.getValue();
        assertThat(savedWaiting.getId()).isNotNull();
        assertThat(savedWaiting.getStoreId()).isEqualTo(storeId);
        assertThat(savedWaiting.getUserId()).isEqualTo(userId);
        assertThat(savedWaiting.getWaitingNumber()).isEqualTo(7L);
        assertThat(savedWaiting.getRequestMessage()).isEqualTo("창가 자리 부탁드립니다.");

        then(waitingSettingRepository).should(never()).findByStoreId(any());
        then(waitingSummaryRepository).should(never()).findByStoreId(any());
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

        WaitingSettingInitializeResponse response = waitingService.initializeWaitingSetting(storeId, request);

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
        WaitingSetting existingSetting = WaitingSetting.builder()
                .storeId(storeId)
                .waitingEnabled(true)
                .maxWaitingCount(20)
                .callTimeoutMinutes(8)
                .allowUserCancel(true)
                .build();
        WaitingSummary existingSummary = WaitingSummary.builder()
                .storeId(storeId)
                .currentWaitingCount(4)
                .averageWaitingMinutes(11)
                .lastWaitingNumber(9L)
                .build();

        given(waitingSettingRepository.findByStoreId(storeId)).willReturn(Optional.of(existingSetting));
        given(waitingSummaryRepository.findByStoreId(storeId)).willReturn(Optional.of(existingSummary));

        WaitingSettingInitializeResponse response = waitingService.initializeWaitingSetting(
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

    private WaitingCreateRequest waitingCreateRequest(UUID storeId, Integer peopleCount, String requestMessage) {
        WaitingCreateRequest request = newInstance(WaitingCreateRequest.class);
        ReflectionTestUtils.setField(request, "storeId", storeId);
        ReflectionTestUtils.setField(request, "peopleCount", peopleCount);
        ReflectionTestUtils.setField(request, "requestMessage", requestMessage);
        return request;
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
