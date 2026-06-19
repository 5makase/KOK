package com.omakase.kok.waiting.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.waiting.domain.entity.Waiting;
import com.omakase.kok.waiting.domain.enums.WaitingStatus;
import com.omakase.kok.waiting.domain.repository.WaitingOutboxEventRepository;
import com.omakase.kok.waiting.domain.repository.WaitingRepository;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore.StoreWaitingValues;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore.WaitingRegistration;
import com.omakase.kok.waiting.presentation.dto.request.WaitingCreateRequest;
import com.omakase.kok.waiting.presentation.dto.response.WaitingResponse;
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

@ExtendWith(MockitoExtension.class)
class WaitingServiceTest {
    @Mock
    private WaitingRepository waitingRepository;

    @Mock
    private WaitingOutboxEventRepository waitingOutboxEventRepository;

    @Mock
    private WaitingQueueRedisStore waitingQueueRedisStore;

    @Mock
    private StoreWaitingService storeWaitingService;

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

        given(storeWaitingService.getStoreWaitingValues(storeId))
                .willReturn(new StoreWaitingValues(true, 50, 10, true, 15));
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

        then(storeWaitingService).should().getStoreWaitingValues(storeId);
    }

    private WaitingCreateRequest waitingCreateRequest(UUID storeId, Integer peopleCount, String requestMessage) {
        WaitingCreateRequest request = newInstance(WaitingCreateRequest.class);
        ReflectionTestUtils.setField(request, "storeId", storeId);
        ReflectionTestUtils.setField(request, "peopleCount", peopleCount);
        ReflectionTestUtils.setField(request, "requestMessage", requestMessage);
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
