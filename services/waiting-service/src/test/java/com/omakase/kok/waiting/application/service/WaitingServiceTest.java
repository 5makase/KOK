package com.omakase.kok.waiting.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.common.dto.PageResponse;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.common.exception.CommonErrorCode;
import com.omakase.kok.waiting.domain.entity.Waiting;
import com.omakase.kok.waiting.domain.enums.WaitingStatus;
import com.omakase.kok.waiting.domain.repository.WaitingOutboxEventRepository;
import com.omakase.kok.waiting.domain.repository.WaitingRepository;
import com.omakase.kok.waiting.global.exception.WaitingErrorCode;
import com.omakase.kok.waiting.global.exception.WaitingException;
import com.omakase.kok.waiting.infrastructure.client.StoreSummaryReader;
import com.omakase.kok.waiting.infrastructure.client.dto.StoreSummaryResponse;
import com.omakase.kok.waiting.infrastructure.messaging.WaitingEventFactory;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore.StoreWaitingValues;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore.WaitingRegistration;
import com.omakase.kok.waiting.presentation.dto.request.WaitingCancelRequest;
import com.omakase.kok.waiting.presentation.dto.request.WaitingCreateRequest;
import com.omakase.kok.waiting.presentation.dto.request.WaitingNoShowRequest;
import com.omakase.kok.waiting.presentation.dto.response.WaitingCallResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingCancelResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingEnterResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingNoShowResponse;
import com.omakase.kok.waiting.presentation.dto.response.StoreWaitingResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private WaitingOutboxEventRepository waitingOutboxEventRepository;

    @Mock
    private WaitingQueueRedisStore waitingQueueRedisStore;

    @Mock
    private WaitingSettingService waitingSettingService;

    @Mock
    private StoreSummaryReader storeSummaryReader;

    @Mock
    private WaitingEventFactory waitingEventFactory;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock callNextLock;

    @InjectMocks
    private WaitingService waitingService;

    @Test
    @DisplayName("웨이팅을 등록하면 Redis 순번 기준으로 웨이팅을 저장하고 응답한다")
    void createWaiting_success() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WaitingCreateRequest request = waitingCreateRequest(storeId, 3, "창가 자리 부탁드립니다.");

        given(storeSummaryReader.getStoreSummary(storeId))
                .willReturn(StoreSummaryResponse.of(storeId, "테스트 매장", UUID.randomUUID()));
        given(waitingSettingService.getStoreWaitingValues(storeId))
                .willReturn(new StoreWaitingValues(true, 50, 10, true, 15));
        given(waitingQueueRedisStore.register(eq(storeId), eq(userId), any(UUID.class), eq(50), any(LocalDate.class)))
                .willReturn(Optional.of(new WaitingRegistration(7L, 3L)));
        given(waitingRepository.saveAndFlush(any(Waiting.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        WaitingResponse response = waitingService.createWaiting(userId, request);

        assertThat(response.getStoreId()).isEqualTo(storeId);
        assertThat(response.getStoreName()).isEqualTo("테스트 매장");
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
        assertThat(savedWaiting.getStoreName()).isEqualTo("테스트 매장");
        assertThat(savedWaiting.getUserId()).isEqualTo(userId);
        assertThat(savedWaiting.getWaitingNumber()).isEqualTo(7L);
        assertThat(savedWaiting.getRequestMessage()).isEqualTo("창가 자리 부탁드립니다.");

        then(waitingSettingService).should().getStoreWaitingValues(storeId);
    }

    @Test
    @DisplayName("본인 매장에는 웨이팅을 등록할 수 없다")
    void createWaiting_failWhenOwnStore() {
        UUID storeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        WaitingCreateRequest request = waitingCreateRequest(storeId, 2, null);

        given(storeSummaryReader.getStoreSummary(storeId))
                .willReturn(StoreSummaryResponse.of(storeId, "내 매장", ownerId));

        assertThatThrownBy(() -> waitingService.createWaiting(ownerId, request))
                .isInstanceOfSatisfying(WaitingException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(WaitingErrorCode.WAITING_OWN_STORE_NOT_ALLOWED));

        then(waitingSettingService).should(never()).getStoreWaitingValues(any(UUID.class));
        then(waitingQueueRedisStore).should(never())
                .register(any(UUID.class), any(UUID.class), any(UUID.class), any(Integer.class), any(LocalDate.class));
        then(waitingRepository).should(never()).saveAndFlush(any(Waiting.class));
    }

    @Test
    @DisplayName("최대 대기 팀 수를 초과하면 웨이팅 등록에 실패한다")
    void createWaiting_failWhenCapacityExceeded() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WaitingCreateRequest request = waitingCreateRequest(storeId, 2, null);

        given(storeSummaryReader.getStoreSummary(storeId))
                .willReturn(StoreSummaryResponse.of(storeId, "테스트 매장", UUID.randomUUID()));
        given(waitingSettingService.getStoreWaitingValues(storeId))
                .willReturn(new StoreWaitingValues(true, 2, 10, true, 15));
        given(waitingQueueRedisStore.register(eq(storeId), eq(userId), any(UUID.class), eq(2), any(LocalDate.class)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> waitingService.createWaiting(userId, request))
                .isInstanceOfSatisfying(WaitingException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(WaitingErrorCode.WAITING_CAPACITY_EXCEEDED));

        then(waitingRepository).should(never()).saveAndFlush(any(Waiting.class));
        then(waitingOutboxEventRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("DB 저장 실패 후 Redis 롤백이 실패해도 원래 저장 예외를 유지한다")
    void createWaiting_keepsOriginalExceptionWhenQueueRollbackFailsAfterDbSaveFailure() {
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WaitingCreateRequest request = waitingCreateRequest(storeId, 2, null);
        RuntimeException dbException = new RuntimeException("db down");
        RuntimeException redisException = new RuntimeException("redis down");

        given(storeSummaryReader.getStoreSummary(storeId))
                .willReturn(StoreSummaryResponse.of(storeId, "테스트 매장", UUID.randomUUID()));
        given(waitingSettingService.getStoreWaitingValues(storeId))
                .willReturn(new StoreWaitingValues(true, 50, 10, true, 15));
        given(waitingQueueRedisStore.register(eq(storeId), eq(userId), any(UUID.class), eq(50), any(LocalDate.class)))
                .willReturn(Optional.of(new WaitingRegistration(1L, 1L)));
        given(waitingRepository.saveAndFlush(any(Waiting.class))).willThrow(dbException);
        org.mockito.Mockito.doThrow(redisException)
                .when(waitingQueueRedisStore).remove(eq(storeId), eq(userId), any(UUID.class), any(LocalDate.class));

        assertThatThrownBy(() -> waitingService.createWaiting(userId, request))
                .isSameAs(dbException)
                .satisfies(exception -> assertThat(exception.getSuppressed()).contains(redisException));

        then(waitingQueueRedisStore).should()
                .remove(eq(storeId), eq(userId), any(UUID.class), any(LocalDate.class));
        then(waitingOutboxEventRepository).should(never()).save(any());
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
        given(waitingQueueRedisStore.getRank(eq(storeId), eq(waiting.getId()), any(LocalDate.class))).willReturn(2L);

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
        given(waitingQueueRedisStore.getRank(eq(storeId), eq(waitingId), any(LocalDate.class))).willReturn(4L);

        var response = waitingService.getWaiting(userId, "USER", waitingId);

        assertThat(response.getWaitingId()).isEqualTo(waitingId);
        assertThat(response.getCurrentRank()).isEqualTo(4L);
        assertThat(response.getTeamsAhead()).isEqualTo(3L);
        assertThat(response.getRequestMessage()).isEqualTo("아기 의자");
    }

    @Test
    @DisplayName("웨이팅 상세 조회는 취소 사유와 노쇼 사유를 포함해 응답한다")
    void getWaiting_includesCancelAndNoShowReason() {
        UUID cancelledWaitingId = UUID.randomUUID();
        UUID noShowWaitingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();

        Waiting cancelledWaiting = waiting(storeId, userId, 8L, WaitingStatus.CANCELLED, null);
        ReflectionTestUtils.setField(cancelledWaiting, "id", cancelledWaitingId);
        ReflectionTestUtils.setField(cancelledWaiting, "cancelReason", "개인 사정");

        Waiting noShowWaiting = waiting(storeId, userId, 9L, WaitingStatus.NO_SHOW, null);
        ReflectionTestUtils.setField(noShowWaiting, "id", noShowWaitingId);
        ReflectionTestUtils.setField(noShowWaiting, "noShowReason", "호출 후 미방문");

        given(waitingRepository.findById(cancelledWaitingId)).willReturn(Optional.of(cancelledWaiting));
        given(waitingRepository.findById(noShowWaitingId)).willReturn(Optional.of(noShowWaiting));

        var cancelledResponse = waitingService.getWaiting(userId, "USER", cancelledWaitingId);
        var noShowResponse = waitingService.getWaiting(userId, "USER", noShowWaitingId);

        assertThat(cancelledResponse.getCancelReason()).isEqualTo("개인 사정");
        assertThat(cancelledResponse.getNoShowReason()).isNull();
        assertThat(noShowResponse.getNoShowReason()).isEqualTo("호출 후 미방문");
        assertThat(noShowResponse.getCancelReason()).isNull();
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
        given(waitingQueueRedisStore.getRank(eq(storeId), eq(waitingId), any(LocalDate.class))).willReturn(1L);

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

        given(storeSummaryReader.getStoreSummary(storeId))
                .willReturn(StoreSummaryResponse.of(storeId, "테스트 매장", userId));
        given(waitingRepository.findByStoreIdAndStatus(storeId, WaitingStatus.CALLED, pageable))
                .willReturn(new PageImpl<>(List.of(waiting), pageable, 1));

        PageResponse<StoreWaitingResponse> response =
                waitingService.getStoreWaitings(userId, "OWNER", storeId, WaitingStatus.CALLED, pageable);

        assertThat(response.content()).hasSize(1);
        StoreWaitingResponse content = response.content().get(0);
        assertThat(content.getWaitingId()).isEqualTo(waiting.getId());
        assertThat(content.getCurrentRank()).isNull();
        assertThat(content.getTeamsAhead()).isNull();
        assertThat(content.getStatus()).isEqualTo(WaitingStatus.CALLED);
    }

    @Test
    @DisplayName("매장별 웨이팅 현황 조회는 본인 매장만 허용한다")
    void getStoreWaitings_forbiddenWhenNotOwner() {
        UUID storeId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        given(storeSummaryReader.getStoreSummary(storeId))
                .willReturn(StoreSummaryResponse.of(storeId, "테스트 매장", UUID.randomUUID()));

        assertThatThrownBy(() -> waitingService.getStoreWaitings(requesterId, "OWNER", storeId, null, PageRequest.of(0, 10)))
                .isInstanceOf(BaseException.class)
                .satisfies(exception -> assertThat(((BaseException) exception).getErrorCode())
                        .isEqualTo(CommonErrorCode.ACCESS_DENIED));

        then(waitingRepository).should(never()).findByStoreId(any(UUID.class), any());
        then(waitingRepository).should(never()).findByStoreIdAndStatus(any(UUID.class), any(), any());
    }

    @Test
    @DisplayName("웨이팅 취소는 본인 웨이팅을 취소하고 Redis 대기열에서 제거한다")
    void cancelWaiting_success() {
        UUID waitingId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Waiting waiting = waiting(storeId, userId, 4L, WaitingStatus.WAITING, "조용한 자리");
        ReflectionTestUtils.setField(waiting, "id", waitingId);
        WaitingCancelRequest request = waitingCancelRequest("개인 사정");

        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));
        given(waitingSettingService.getStoreWaitingValues(storeId))
                .willReturn(new StoreWaitingValues(true, 50, 10, true, 15));
        given(waitingRepository.save(any(Waiting.class))).willAnswer(invocation -> invocation.getArgument(0));

        WaitingCancelResponse response = waitingService.cancelWaiting(userId, "USER", waitingId, request);

        assertThat(response.getWaitingId()).isEqualTo(waitingId);
        assertThat(response.getWaitingNumber()).isEqualTo(4L);
        assertThat(response.getStatus()).isEqualTo(WaitingStatus.CANCELLED);
        assertThat(response.getCancelReason()).isEqualTo("개인 사정");
        assertThat(response.getCancelledAt()).isNotNull();

        then(waitingRepository).should().save(waiting);
        then(waitingQueueRedisStore).should().remove(eq(storeId), eq(userId), eq(waitingId), any(LocalDate.class));
    }

    @Test
    @DisplayName("마스터는 본인 웨이팅이 아니어도 취소할 수 있다")
    void cancelWaiting_masterCanAccess() {
        UUID waitingId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID masterId = UUID.randomUUID();
        Waiting waiting = waiting(storeId, ownerId, 9L, WaitingStatus.CALLED, null);
        ReflectionTestUtils.setField(waiting, "id", waitingId);
        ReflectionTestUtils.setField(waiting, "calledAt", LocalDateTime.now());
        WaitingCancelRequest request = waitingCancelRequest("관리자 취소");

        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));
        given(waitingRepository.save(any(Waiting.class))).willAnswer(invocation -> invocation.getArgument(0));

        WaitingCancelResponse response = waitingService.cancelWaiting(masterId, "MASTER", waitingId, request);

        assertThat(response.getStatus()).isEqualTo(WaitingStatus.CANCELLED);
        assertThat(response.getCancelReason()).isEqualTo("관리자 취소");
        then(waitingSettingService).should(never()).getStoreWaitingValues(any(UUID.class));
        then(waitingQueueRedisStore).should().remove(eq(storeId), eq(ownerId), eq(waitingId), any(LocalDate.class));
    }

    @Test
    @DisplayName("웨이팅 취소는 본인 웨이팅만 허용한다")
    void cancelWaiting_forbiddenWhenNotOwner() {
        UUID waitingId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        Waiting waiting = waiting(storeId, ownerId, 2L, WaitingStatus.WAITING, null);
        ReflectionTestUtils.setField(waiting, "id", waitingId);
        WaitingCancelRequest request = waitingCancelRequest("취소 요청");

        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));

        assertThatThrownBy(() -> waitingService.cancelWaiting(requesterId, "USER", waitingId, request))
                .isInstanceOf(BaseException.class)
                .satisfies(exception -> assertThat(((BaseException) exception).getErrorCode())
                        .isEqualTo(CommonErrorCode.ACCESS_DENIED));

        then(waitingSettingService).should(never()).getStoreWaitingValues(any(UUID.class));
        then(waitingRepository).should(never()).save(any(Waiting.class));
        then(waitingQueueRedisStore).should(never()).remove(any(UUID.class), any(UUID.class), any(UUID.class), any(LocalDate.class));
    }

    @Test
    @DisplayName("웨이팅 취소는 매장 설정에서 사용자 취소가 비활성화되면 실패한다")
    void cancelWaiting_disabledWhenStoreDoesNotAllowUserCancel() {
        UUID waitingId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Waiting waiting = waiting(storeId, userId, 6L, WaitingStatus.WAITING, null);
        ReflectionTestUtils.setField(waiting, "id", waitingId);
        WaitingCancelRequest request = waitingCancelRequest("취소 요청");

        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));
        given(waitingSettingService.getStoreWaitingValues(storeId))
                .willReturn(new StoreWaitingValues(true, 50, 10, false, 15));

        assertThatThrownBy(() -> waitingService.cancelWaiting(userId, "USER", waitingId, request))
                .isInstanceOfSatisfying(WaitingException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(WaitingErrorCode.WAITING_CANCEL_DISABLED));

        then(waitingRepository).should(never()).save(any(Waiting.class));
        then(waitingQueueRedisStore).should(never()).remove(any(UUID.class), any(UUID.class), any(UUID.class), any(LocalDate.class));
    }

    @Test
    @DisplayName("마스터는 사용자 취소 비활성화 매장이어도 웨이팅을 취소할 수 있다")
    void cancelWaiting_masterBypassesUserCancelPolicy() {
        UUID waitingId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID masterId = UUID.randomUUID();
        Waiting waiting = waiting(storeId, ownerId, 7L, WaitingStatus.WAITING, null);
        ReflectionTestUtils.setField(waiting, "id", waitingId);
        WaitingCancelRequest request = waitingCancelRequest("관리자 취소");

        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));
        given(waitingRepository.save(any(Waiting.class))).willAnswer(invocation -> invocation.getArgument(0));

        WaitingCancelResponse response = waitingService.cancelWaiting(masterId, "MASTER", waitingId, request);

        assertThat(response.getStatus()).isEqualTo(WaitingStatus.CANCELLED);
        then(waitingSettingService).should(never()).getStoreWaitingValues(any(UUID.class));
        then(waitingQueueRedisStore).should().remove(eq(storeId), eq(ownerId), eq(waitingId), any(LocalDate.class));
    }

    @Test
    @DisplayName("다음 순번 호출은 Redis 대기열의 첫 번째 웨이팅을 호출 처리한다")
    void callNextWaiting_success() throws InterruptedException {
        UUID storeId = UUID.randomUUID();
        UUID waitingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Waiting waiting = waiting(storeId, userId, 10L, WaitingStatus.WAITING, "문 앞 자리");
        ReflectionTestUtils.setField(waiting, "id", waitingId);

        given(storeSummaryReader.getStoreSummary(storeId))
                .willReturn(StoreSummaryResponse.of(storeId, "테스트 매장", userId));
        givenCallNextLockAcquired(storeId);
        given(waitingQueueRedisStore.findFirst(storeId)).willReturn(Optional.of(waitingId));
        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));
        given(waitingRepository.save(any(Waiting.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(waitingSettingService.getStoreWaitingValues(storeId))
                .willReturn(new StoreWaitingValues(true, 50, 10, true, 15));

        WaitingCallResponse response = waitingService.callNextWaiting(userId, "OWNER", storeId);

        assertThat(response.getWaitingId()).isEqualTo(waitingId);
        assertThat(response.getStoreId()).isEqualTo(storeId);
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getWaitingNumber()).isEqualTo(10L);
        assertThat(response.getStatus()).isEqualTo(WaitingStatus.CALLED);
        assertThat(response.getCalledAt()).isNotNull();
        assertThat(response.getCallExpiresAt()).isEqualTo(response.getCalledAt().plusMinutes(10));
        assertThat(waiting.getCallExpiresAt()).isEqualTo(waiting.getCalledAt().plusMinutes(10));

        then(waitingRepository).should().save(waiting);
        then(waitingQueueRedisStore).should().remove(eq(storeId), eq(userId), eq(waitingId), any(LocalDate.class));
        then(callNextLock).should().unlock();
    }

    @Test
    @DisplayName("다음 순번 호출은 대기열이 비어 있으면 실패한다")
    void callNextWaiting_notFoundWhenQueueEmpty() throws InterruptedException {
        UUID storeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        given(storeSummaryReader.getStoreSummary(storeId))
                .willReturn(StoreSummaryResponse.of(storeId, "테스트 매장", ownerId));
        givenCallNextLockAcquired(storeId);
        given(waitingQueueRedisStore.findFirst(storeId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> waitingService.callNextWaiting(ownerId, "OWNER", storeId))
                .isInstanceOfSatisfying(WaitingException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(WaitingErrorCode.WAITING_CALL_TARGET_NOT_FOUND));

        then(waitingRepository).should(never()).findById(any(UUID.class));
        then(waitingRepository).should(never()).save(any(Waiting.class));
        then(callNextLock).should().unlock();
    }

    @Test
    @DisplayName("다음 순번 호출은 이미 호출된 웨이팅이면 실패한다")
    void callNextWaiting_failWhenAlreadyCalled() throws InterruptedException {
        UUID storeId = UUID.randomUUID();
        UUID waitingId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Waiting waiting = waiting(storeId, UUID.randomUUID(), 11L, WaitingStatus.CALLED, null);
        ReflectionTestUtils.setField(waiting, "id", waitingId);
        ReflectionTestUtils.setField(waiting, "calledAt", LocalDateTime.now());

        given(storeSummaryReader.getStoreSummary(storeId))
                .willReturn(StoreSummaryResponse.of(storeId, "테스트 매장", ownerId));
        givenCallNextLockAcquired(storeId);
        given(waitingQueueRedisStore.findFirst(storeId)).willReturn(Optional.of(waitingId));
        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));

        assertThatThrownBy(() -> waitingService.callNextWaiting(ownerId, "OWNER", storeId))
                .isInstanceOfSatisfying(WaitingException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(WaitingErrorCode.WAITING_CALL_NOT_ALLOWED));

        then(waitingRepository).should(never()).save(any(Waiting.class));
        then(callNextLock).should().unlock();
    }

    @Test
    @DisplayName("다음 순번 호출은 락 획득에 실패하면 대기열을 조회하지 않고 실패한다")
    void callNextWaiting_failWhenLockNotAcquired() throws InterruptedException {
        UUID storeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        given(storeSummaryReader.getStoreSummary(storeId))
                .willReturn(StoreSummaryResponse.of(storeId, "테스트 매장", ownerId));
        given(redissonClient.getLock("waiting:store:" + storeId + ":call-next:lock")).willReturn(callNextLock);
        given(callNextLock.tryLock(0L, TimeUnit.SECONDS)).willReturn(false);

        assertThatThrownBy(() -> waitingService.callNextWaiting(ownerId, "OWNER", storeId))
                .isInstanceOfSatisfying(WaitingException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(WaitingErrorCode.WAITING_CALL_LOCK_FAILED));

        then(waitingQueueRedisStore).should(never()).findFirst(any(UUID.class));
        then(waitingRepository).should(never()).findById(any(UUID.class));
        then(waitingRepository).should(never()).save(any(Waiting.class));
        then(callNextLock).should(never()).unlock();
    }

    @Test
    @DisplayName("입장 완료 처리는 CALLED 상태 웨이팅을 ENTERED로 변경한다")
    void enterWaiting_success() {
        UUID waitingId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Waiting waiting = waiting(storeId, UUID.randomUUID(), 13L, WaitingStatus.CALLED, null);
        ReflectionTestUtils.setField(waiting, "id", waitingId);
        ReflectionTestUtils.setField(waiting, "calledAt", LocalDateTime.now());

        given(storeSummaryReader.getStoreSummary(storeId))
                .willReturn(StoreSummaryResponse.of(storeId, "테스트 매장", ownerId));
        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));
        given(waitingRepository.save(any(Waiting.class))).willAnswer(invocation -> invocation.getArgument(0));

        WaitingEnterResponse response = waitingService.enterWaiting(ownerId, "OWNER", waitingId);

        assertThat(response.getWaitingId()).isEqualTo(waitingId);
        assertThat(response.getStatus()).isEqualTo(WaitingStatus.ENTERED);
        assertThat(response.getEnteredAt()).isNotNull();

        then(waitingRepository).should().save(waiting);
        then(waitingQueueRedisStore).should().remove(eq(storeId), eq(waiting.getUserId()), eq(waitingId), any(LocalDate.class));
    }

    @Test
    @DisplayName("입장 완료 처리는 CALLED 상태가 아니면 실패한다")
    void enterWaiting_failWhenNotCalled() {
        UUID waitingId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Waiting waiting = waiting(storeId, UUID.randomUUID(), 14L, WaitingStatus.WAITING, null);
        ReflectionTestUtils.setField(waiting, "id", waitingId);

        given(storeSummaryReader.getStoreSummary(storeId))
                .willReturn(StoreSummaryResponse.of(storeId, "테스트 매장", ownerId));
        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));

        assertThatThrownBy(() -> waitingService.enterWaiting(ownerId, "OWNER", waitingId))
                .isInstanceOfSatisfying(WaitingException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(WaitingErrorCode.WAITING_ENTER_NOT_ALLOWED));

        then(waitingRepository).should(never()).save(any(Waiting.class));
        then(waitingQueueRedisStore).should(never()).remove(any(UUID.class), any(UUID.class), any(UUID.class), any(LocalDate.class));
    }

    @Test
    @DisplayName("미입장 처리는 CALLED 상태 웨이팅을 NO_SHOW로 변경한다")
    void noShowWaiting_success() {
        UUID waitingId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Waiting waiting = waiting(storeId, UUID.randomUUID(), 15L, WaitingStatus.CALLED, null);
        ReflectionTestUtils.setField(waiting, "id", waitingId);
        ReflectionTestUtils.setField(waiting, "calledAt", LocalDateTime.now());
        WaitingNoShowRequest request = waitingNoShowRequest("호출 후 미방문");

        given(storeSummaryReader.getStoreSummary(storeId))
                .willReturn(StoreSummaryResponse.of(storeId, "테스트 매장", ownerId));
        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));
        given(waitingRepository.save(any(Waiting.class))).willAnswer(invocation -> invocation.getArgument(0));

        WaitingNoShowResponse response = waitingService.noShowWaiting(ownerId, "OWNER", waitingId, request);

        assertThat(response.getWaitingId()).isEqualTo(waitingId);
        assertThat(response.getStatus()).isEqualTo(WaitingStatus.NO_SHOW);
        assertThat(response.getReason()).isEqualTo("호출 후 미방문");
        assertThat(response.getNoShowAt()).isNotNull();

        then(waitingRepository).should().save(waiting);
        then(waitingQueueRedisStore).should().remove(eq(storeId), eq(waiting.getUserId()), eq(waitingId), any(LocalDate.class));
    }

    @Test
    @DisplayName("미입장 처리는 CALLED 상태가 아니면 실패한다")
    void noShowWaiting_failWhenNotCalled() {
        UUID waitingId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Waiting waiting = waiting(storeId, UUID.randomUUID(), 16L, WaitingStatus.WAITING, null);
        ReflectionTestUtils.setField(waiting, "id", waitingId);
        WaitingNoShowRequest request = waitingNoShowRequest("부재");

        given(storeSummaryReader.getStoreSummary(storeId))
                .willReturn(StoreSummaryResponse.of(storeId, "테스트 매장", ownerId));
        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));

        assertThatThrownBy(() -> waitingService.noShowWaiting(ownerId, "OWNER", waitingId, request))
                .isInstanceOfSatisfying(WaitingException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(WaitingErrorCode.WAITING_NO_SHOW_NOT_ALLOWED));

        then(waitingRepository).should(never()).save(any(Waiting.class));
        then(waitingQueueRedisStore).should(never()).remove(any(UUID.class), any(UUID.class), any(UUID.class), any(LocalDate.class));
    }

    @Test
    @DisplayName("자동 미입장 처리는 호출 만료 시간이 지난 CALLED 웨이팅을 NO_SHOW로 변경한다")
    void autoNoShowExpiredWaitings_success() {
        UUID storeId = UUID.randomUUID();
        UUID waitingId = UUID.randomUUID();
        Waiting waiting = waiting(storeId, UUID.randomUUID(), 18L, WaitingStatus.CALLED, null);
        ReflectionTestUtils.setField(waiting, "id", waitingId);
        ReflectionTestUtils.setField(waiting, "calledAt", LocalDateTime.now().minusMinutes(11));
        ReflectionTestUtils.setField(waiting, "callExpiresAt", LocalDateTime.now().minusMinutes(1));
        waiting.noShow("호출 제한 시간 초과");

        given(waitingRepository.findExpiredCalledWaitingIds(
                eq(WaitingStatus.CALLED),
                any(LocalDateTime.class),
                eq(PageRequest.of(0, 100))
        )).willReturn(List.of(waitingId));
        given(waitingRepository.markNoShowIfExpired(
                eq(waitingId),
                eq(WaitingStatus.CALLED),
                eq(WaitingStatus.NO_SHOW),
                eq("호출 제한 시간 초과"),
                eq(UUID.fromString("00000000-0000-0000-0000-000000000000")),
                any(LocalDateTime.class)
        )).willReturn(1);
        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));

        int processedCount = waitingService.autoNoShowExpiredWaitings(100);

        assertThat(processedCount).isEqualTo(1);
        assertThat(waiting.getStatus()).isEqualTo(WaitingStatus.NO_SHOW);
        assertThat(waiting.getNoShowReason()).isEqualTo("호출 제한 시간 초과");
        assertThat(waiting.getNoShowedAt()).isNotNull();
        then(waitingRepository).should(never()).save(any(Waiting.class));
        then(waitingQueueRedisStore).should().remove(eq(storeId), eq(waiting.getUserId()), eq(waitingId), any(LocalDate.class));
        then(waitingOutboxEventRepository).should().save(any());
    }

    @Test
    @DisplayName("자동 미입장 처리는 조회 후 상태가 바뀐 웨이팅이면 Outbox를 저장하지 않는다")
    void autoNoShowExpiredWaitings_skipWhenConcurrentOwnerActionWins() {
        UUID waitingId = UUID.randomUUID();

        given(waitingRepository.findExpiredCalledWaitingIds(
                eq(WaitingStatus.CALLED),
                any(LocalDateTime.class),
                eq(PageRequest.of(0, 100))
        )).willReturn(List.of(waitingId));
        given(waitingRepository.markNoShowIfExpired(
                eq(waitingId),
                eq(WaitingStatus.CALLED),
                eq(WaitingStatus.NO_SHOW),
                eq("호출 제한 시간 초과"),
                eq(UUID.fromString("00000000-0000-0000-0000-000000000000")),
                any(LocalDateTime.class)
        )).willReturn(0);

        int processedCount = waitingService.autoNoShowExpiredWaitings(100);

        assertThat(processedCount).isZero();
        then(waitingRepository).should(never()).findById(waitingId);
        then(waitingRepository).should(never()).save(any(Waiting.class));
        then(waitingQueueRedisStore).should(never()).remove(any(UUID.class), any(UUID.class), any(UUID.class), any(LocalDate.class));
        then(waitingOutboxEventRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("커밋 후 Redis 정리에 실패해도 상태 변경 요청은 성공한다")
    void cancelWaiting_succeedsEvenWhenQueueCleanupFailsAfterCommit() {
        UUID waitingId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Waiting waiting = waiting(storeId, userId, 17L, WaitingStatus.WAITING, null);
        ReflectionTestUtils.setField(waiting, "id", waitingId);
        WaitingCancelRequest request = waitingCancelRequest("개인 사정");

        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));
        given(waitingSettingService.getStoreWaitingValues(storeId))
                .willReturn(new StoreWaitingValues(true, 50, 10, true, 15));
        given(waitingRepository.save(any(Waiting.class))).willAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("redis down"))
                .when(waitingQueueRedisStore).remove(eq(storeId), eq(userId), eq(waitingId), any(LocalDate.class));

        TransactionSynchronizationManager.initSynchronization();
        try {
            WaitingCancelResponse response = waitingService.cancelWaiting(userId, "USER", waitingId, request);

            assertThat(response.getStatus()).isEqualTo(WaitingStatus.CANCELLED);
            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);

            assertThatCode(() -> synchronizations.forEach(TransactionSynchronization::afterCommit))
                    .doesNotThrowAnyException();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
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

    private WaitingCancelRequest waitingCancelRequest(String cancelReason) {
        WaitingCancelRequest request = newInstance(WaitingCancelRequest.class);
        ReflectionTestUtils.setField(request, "cancelReason", cancelReason);
        return request;
    }

    private WaitingNoShowRequest waitingNoShowRequest(String reason) {
        WaitingNoShowRequest request = newInstance(WaitingNoShowRequest.class);
        ReflectionTestUtils.setField(request, "reason", reason);
        return request;
    }

    private Waiting waiting(UUID storeId, UUID userId, Long waitingNumber, WaitingStatus status, String requestMessage) {
        Waiting waiting = Waiting.builder()
                .id(UUID.randomUUID())
                .storeId(storeId)
                .storeName("테스트 매장")
                .userId(userId)
                .waitingNumber(waitingNumber)
                .peopleCount(2)
                .requestMessage(requestMessage)
                .build();
        ReflectionTestUtils.setField(waiting, "status", status);
        ReflectionTestUtils.setField(waiting, "createdAt", LocalDateTime.now());
        return waiting;
    }

    private void givenCallNextLockAcquired(UUID storeId) throws InterruptedException {
        given(redissonClient.getLock("waiting:store:" + storeId + ":call-next:lock")).willReturn(callNextLock);
        given(callNextLock.tryLock(0L, TimeUnit.SECONDS)).willReturn(true);
        given(callNextLock.isHeldByCurrentThread()).willReturn(true);
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
