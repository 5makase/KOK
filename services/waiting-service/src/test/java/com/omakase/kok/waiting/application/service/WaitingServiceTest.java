package com.omakase.kok.waiting.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.common.dto.PageResponse;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.common.exception.CommonErrorCode;
import com.omakase.kok.waiting.domain.entity.Waiting;
import com.omakase.kok.waiting.domain.enums.WaitingStatus;
import com.omakase.kok.waiting.domain.repository.WaitingOutboxEventRepository;
import com.omakase.kok.waiting.domain.repository.WaitingRepository;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore.StoreWaitingValues;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore.WaitingRegistration;
import com.omakase.kok.waiting.presentation.dto.request.WaitingCreateRequest;
import com.omakase.kok.waiting.presentation.dto.response.StoreWaitingResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private WaitingSettingService waitingSettingService;

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

        given(waitingSettingService.getStoreWaitingValues(storeId))
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

        ArgumentCaptor<Waiting> waitingCaptor = ArgumentCaptor.forClass(Waiting.class);
        then(waitingRepository).should().saveAndFlush(waitingCaptor.capture());
        Waiting savedWaiting = waitingCaptor.getValue();
        assertThat(savedWaiting.getId()).isNotNull();
        assertThat(savedWaiting.getStoreId()).isEqualTo(storeId);
        assertThat(savedWaiting.getUserId()).isEqualTo(userId);
        assertThat(savedWaiting.getWaitingNumber()).isEqualTo(7L);
        assertThat(savedWaiting.getRequestMessage()).isEqualTo("창가 자리 부탁드립니다.");

        then(waitingSettingService).should().getStoreWaitingValues(storeId);
    }

    @Test
    @DisplayName("내 웨이팅 목록 조회는 상태 필터와 현재 순번을 포함해 응답한다")
    void getMyWaitings_success() {
        UUID userId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        Waiting waiting = waiting(storeId, userId, 5L, WaitingStatus.WAITING, "요청사항");
        PageRequest pageable = PageRequest.of(0, 10);

        given(waitingRepository.findByUserIdAndStatus(userId, WaitingStatus.WAITING, pageable))
                .willReturn(new PageImpl<>(List.of(waiting), pageable, 1));
        given(waitingQueueRedisStore.getRank(storeId, waiting.getId())).willReturn(2L);

        PageResponse<WaitingResponse> response = waitingService.getMyWaitings(userId, WaitingStatus.WAITING, pageable);

        assertThat(response.content()).hasSize(1);
        WaitingResponse content = response.content().get(0);
        assertThat(content.getWaitingId()).isEqualTo(waiting.getId());
        assertThat(content.getCurrentRank()).isEqualTo(2L);
        assertThat(content.getTeamsAhead()).isEqualTo(1L);
        assertThat(content.getStatus()).isEqualTo(WaitingStatus.WAITING);
    }

    @Test
    @DisplayName("웨이팅 상세 조회는 본인 웨이팅만 허용한다")
    void getWaiting_forbiddenWhenNotOwner() {
        UUID waitingId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        Waiting waiting = waiting(UUID.randomUUID(), ownerId, 1L, WaitingStatus.WAITING, null);

        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));

        assertThatThrownBy(() -> waitingService.getWaiting(requesterId, "USER", waitingId))
                .isInstanceOf(BaseException.class)
                .satisfies(exception -> assertThat(((BaseException) exception).getErrorCode())
                        .isEqualTo(CommonErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("웨이팅 상세 조회는 현재 순번을 포함해 응답한다")
    void getWaiting_success() {
        UUID waitingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        Waiting waiting = waiting(storeId, userId, 8L, WaitingStatus.WAITING, "아기 의자");
        ReflectionTestUtils.setField(waiting, "id", waitingId);

        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));
        given(waitingQueueRedisStore.getRank(storeId, waitingId)).willReturn(4L);

        var response = waitingService.getWaiting(userId, "USER", waitingId);

        assertThat(response.getWaitingId()).isEqualTo(waitingId);
        assertThat(response.getCurrentRank()).isEqualTo(4L);
        assertThat(response.getTeamsAhead()).isEqualTo(3L);
        assertThat(response.getRequestMessage()).isEqualTo("아기 의자");
    }

    @Test
    @DisplayName("마스터는 본인 웨이팅이 아니어도 상세 조회할 수 있다")
    void getWaiting_masterCanAccess() {
        UUID waitingId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID masterId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        Waiting waiting = waiting(storeId, ownerId, 2L, WaitingStatus.WAITING, "빠른 안내 요청");
        ReflectionTestUtils.setField(waiting, "id", waitingId);

        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));
        given(waitingQueueRedisStore.getRank(storeId, waitingId)).willReturn(1L);

        var response = waitingService.getWaiting(masterId, "MASTER", waitingId);

        assertThat(response.getWaitingId()).isEqualTo(waitingId);
        assertThat(response.getUserId()).isEqualTo(ownerId);
        assertThat(response.getCurrentRank()).isEqualTo(1L);
    }

    @Test
    @DisplayName("매장별 웨이팅 현황 조회는 상태 필터와 현재 순번을 포함해 응답한다")
    void getStoreWaitings_success() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Waiting waiting = waiting(storeId, userId, 3L, WaitingStatus.CALLED, "늦으면 연락 부탁");
        ReflectionTestUtils.setField(waiting, "calledAt", LocalDateTime.now());
        PageRequest pageable = PageRequest.of(0, 20);

        given(waitingRepository.findByStoreIdAndStatus(storeId, WaitingStatus.CALLED, pageable))
                .willReturn(new PageImpl<>(List.of(waiting), pageable, 1));
        given(waitingQueueRedisStore.getRank(storeId, waiting.getId())).willReturn(1L);

        PageResponse<StoreWaitingResponse> response = waitingService.getStoreWaitings(storeId, WaitingStatus.CALLED, pageable);

        assertThat(response.content()).hasSize(1);
        StoreWaitingResponse content = response.content().get(0);
        assertThat(content.getWaitingId()).isEqualTo(waiting.getId());
        assertThat(content.getCurrentRank()).isEqualTo(1L);
        assertThat(content.getTeamsAhead()).isEqualTo(0L);
        assertThat(content.getStatus()).isEqualTo(WaitingStatus.CALLED);
    }

    @Test
    @DisplayName("순번 임박 알림 대상 조회는 Redis 순서를 유지해 현재 순번을 포함해 응답한다")
    void getNearTurnWaitings_success() {
        UUID storeId = UUID.randomUUID();
        UUID firstWaitingId = UUID.randomUUID();
        UUID secondWaitingId = UUID.randomUUID();
        Waiting firstWaiting = waiting(storeId, UUID.randomUUID(), 11L, WaitingStatus.WAITING, null);
        Waiting secondWaiting = waiting(storeId, UUID.randomUUID(), 12L, WaitingStatus.CALLED, null);
        ReflectionTestUtils.setField(firstWaiting, "id", firstWaitingId);
        ReflectionTestUtils.setField(secondWaiting, "id", secondWaitingId);

        given(waitingQueueRedisStore.findNearTurn(storeId, 2))
                .willReturn(List.of(firstWaitingId, secondWaitingId));
        given(waitingRepository.findAllById(List.of(firstWaitingId, secondWaitingId)))
                .willReturn(List.of(secondWaiting, firstWaiting));

        var response = waitingService.getNearTurnWaitings(storeId, 2);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).getWaitingId()).isEqualTo(firstWaitingId);
        assertThat(response.get(0).getUserId()).isEqualTo(firstWaiting.getUserId());
        assertThat(response.get(0).getCurrentRank()).isEqualTo(1L);
        assertThat(response.get(1).getWaitingId()).isEqualTo(secondWaitingId);
        assertThat(response.get(1).getUserId()).isEqualTo(secondWaiting.getUserId());
        assertThat(response.get(1).getCurrentRank()).isEqualTo(2L);
    }

    @Test
    @DisplayName("순번 임박 알림 대상 조회는 Redis 대기열이 비어 있으면 빈 목록을 반환한다")
    void getNearTurnWaitings_empty() {
        UUID storeId = UUID.randomUUID();

        given(waitingQueueRedisStore.findNearTurn(storeId, 3)).willReturn(List.of());

        var response = waitingService.getNearTurnWaitings(storeId, 3);

        assertThat(response).isEmpty();
    }

    private WaitingCreateRequest waitingCreateRequest(UUID storeId, Integer peopleCount, String requestMessage) {
        WaitingCreateRequest request = newInstance(WaitingCreateRequest.class);
        ReflectionTestUtils.setField(request, "storeId", storeId);
        ReflectionTestUtils.setField(request, "peopleCount", peopleCount);
        ReflectionTestUtils.setField(request, "requestMessage", requestMessage);
        return request;
    }

    private Waiting waiting(UUID storeId, UUID userId, Long waitingNumber, WaitingStatus status, String requestMessage) {
        Waiting waiting = Waiting.builder()
                .id(UUID.randomUUID())
                .storeId(storeId)
                .storeName("테스트 매장")
                .userId(userId)
                .visitorName("테스터")
                .waitingNumber(waitingNumber)
                .peopleCount(2)
                .requestMessage(requestMessage)
                .build();
        ReflectionTestUtils.setField(waiting, "status", status);
        ReflectionTestUtils.setField(waiting, "createdAt", LocalDateTime.now());
        return waiting;
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
