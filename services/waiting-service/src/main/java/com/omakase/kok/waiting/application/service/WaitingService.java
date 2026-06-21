package com.omakase.kok.waiting.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.common.auth.AuthConstants;
import com.omakase.kok.common.auth.RoleAuthorizationUtils;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.common.exception.CommonErrorCode;
import com.omakase.kok.common.dto.PageResponse;
import com.omakase.kok.waiting.domain.entity.Waiting;
import com.omakase.kok.waiting.domain.entity.WaitingOutboxEvent;
import com.omakase.kok.waiting.domain.enums.WaitingEventType;
import com.omakase.kok.waiting.domain.enums.WaitingStatus;
import com.omakase.kok.waiting.domain.repository.WaitingOutboxEventRepository;
import com.omakase.kok.waiting.domain.repository.WaitingRepository;
import com.omakase.kok.waiting.global.exception.WaitingErrorCode;
import com.omakase.kok.waiting.global.exception.WaitingException;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore.StoreWaitingValues;
import com.omakase.kok.waiting.infrastructure.redis.WaitingQueueRedisStore.WaitingRegistration;
import com.omakase.kok.waiting.presentation.dto.request.WaitingCancelRequest;
import com.omakase.kok.waiting.presentation.dto.request.WaitingCreateRequest;
import com.omakase.kok.waiting.presentation.dto.request.WaitingNoShowRequest;
import com.omakase.kok.waiting.presentation.dto.response.NearTurnWaitingResponse;
import com.omakase.kok.waiting.presentation.dto.response.StoreWaitingResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingCallResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingCancelResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingDetailResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingEnterResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingNoShowResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class WaitingService {
    private final WaitingRepository waitingRepository;
    private final WaitingOutboxEventRepository waitingOutboxEventRepository;
    private final WaitingQueueRedisStore waitingQueueRedisStore;
    private final WaitingSettingService waitingSettingService;
    private final ObjectMapper objectMapper;

    // 웨이팅 등록
    @Transactional
    public WaitingResponse createWaiting(UUID userId, WaitingCreateRequest request) {
        UUID storeId = request.getStoreId();

        // TODO: Store Service 내부 API 연동 후 본인 매장 웨이팅 등록 제한 검증 추가

        // 매장 웨이팅 설정/평균 대기시간 조회
        StoreWaitingValues storeWaitingValues = waitingSettingService.getStoreWaitingValues(storeId);
        // 웨이팅 활성화 여부 검증
        validateWaitingEnabled(storeWaitingValues);
        // Redis 기준 웨이팅 등록
        UUID waitingId = UUID.randomUUID();
        WaitingRegistration registration = waitingQueueRedisStore.register(
                storeId,
                userId,
                waitingId,
                storeWaitingValues.maxWaitingCount()
        ).orElseThrow(() -> new WaitingException(WaitingErrorCode.WAITING_CAPACITY_EXCEEDED));
        // 웨이팅 생성
        Waiting waiting = Waiting.builder()
                .id(waitingId)
                .storeId(storeId)
                // TODO: Store Service 내부 API 연동 후 매장명 스냅샷 저장
                .storeName("UNKNOWN")
                .userId(userId)
                // TODO: User Service 내부 API 연동 후 방문자명 스냅샷 저장
                .visitorName("UNKNOWN")
                .waitingNumber(registration.waitingNumber())
                .peopleCount(request.getPeopleCount())
                .requestMessage(request.getRequestMessage())
                .build();
        Waiting savedWaiting = saveWaitingWithQueueRollback(storeId, waiting);
        // TODO: Kafka Outbox Publisher 도입 시 WAITING_REGISTERED 이벤트 저장
        // saveOutboxEvent(savedWaiting, WaitingEventType.WAITING_REGISTERED);

        return WaitingResponse.of(savedWaiting, registration.currentRank());
    }

    // 내 웨이팅 목록 조회
    public PageResponse<WaitingResponse> getMyWaitings(UUID userId, WaitingStatus status, Pageable pageable) {
        Page<Waiting> waitings = status == null
                ? waitingRepository.findByUserId(userId, pageable)
                : waitingRepository.findByUserIdAndStatus(userId, status, pageable);

        return PageResponse.from(waitings.map(waiting -> WaitingResponse.of(waiting, resolveCurrentRank(waiting))));
    }

    // 웨이팅 상세 조회
    public WaitingDetailResponse getWaiting(UUID userId, String role, UUID waitingId) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new WaitingException(WaitingErrorCode.WAITING_NOT_FOUND));
        validateWaitingAccess(userId, role, waiting);

        return WaitingDetailResponse.of(waiting, resolveCurrentRank(waiting));
    }

    // 매장별 웨이팅 목록 조회
    public PageResponse<StoreWaitingResponse> getStoreWaitings(
            UUID userId,
            UUID storeId,
            WaitingStatus status,
            Pageable pageable
    ) {
        // TODO: Store Service 내부 API 연동 후 요청 userId가 storeId의 소유자인지 검증 추가
        Page<Waiting> waitings = status == null
                ? waitingRepository.findByStoreId(storeId, pageable)
                : waitingRepository.findByStoreIdAndStatus(storeId, status, pageable);
        return PageResponse.from(waitings.map(waiting -> StoreWaitingResponse.of(waiting, resolveCurrentRank(waiting))));
    }

    // 웨이팅 취소
    @Transactional
    public WaitingCancelResponse cancelWaiting(UUID userId, String role, UUID waitingId, WaitingCancelRequest request) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new WaitingException(WaitingErrorCode.WAITING_NOT_FOUND));
        validateWaitingAccess(userId, role, waiting);
        validateUserCancelable(role, waiting);

        waiting.cancel(request.getCancelReason());
        Waiting savedWaiting = waitingRepository.save(waiting);
        scheduleQueueRemovalAfterCommit(savedWaiting);
        // TODO: Kafka Outbox Publisher 도입 시 WAITING_CANCELLED 이벤트 저장
        // saveOutboxEvent(savedWaiting, WaitingEventType.WAITING_CANCELLED);

        return WaitingCancelResponse.from(savedWaiting);
    }

    // 다음 순번 웨이팅 호출
    @Transactional
    public WaitingCallResponse callNextWaiting(UUID storeId) {
        // TODO: Store Service 내부 API 연동 후 요청 userId가 storeId의 소유자인지 검증 추가
        UUID waitingId = waitingQueueRedisStore.findFirst(storeId)
                .orElseThrow(() -> new WaitingException(WaitingErrorCode.WAITING_CALL_TARGET_NOT_FOUND));
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new WaitingException(WaitingErrorCode.WAITING_CALL_TARGET_NOT_FOUND));

        waiting.call();
        Waiting savedWaiting = waitingRepository.save(waiting);
        scheduleQueueRemovalAfterCommit(savedWaiting);
        // TODO: Kafka Outbox Publisher 도입 시 WAITING_CALLED 이벤트 저장
        // saveOutboxEvent(savedWaiting, WaitingEventType.WAITING_CALLED);

        return WaitingCallResponse.from(savedWaiting);
    }

    // 웨이팅 입장 완료 처리
    @Transactional
    public WaitingEnterResponse enterWaiting(UUID userId, UUID waitingId) {
        // TODO: Store Service 내부 API 연동 후 요청 userId가 waitingId의 storeId 소유자인지 검증 추가
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new WaitingException(WaitingErrorCode.WAITING_NOT_FOUND));

        waiting.enter();
        Waiting savedWaiting = waitingRepository.save(waiting);
        scheduleQueueRemovalAfterCommit(savedWaiting);
        // TODO: Kafka Outbox Publisher 도입 시 WAITING_ENTERED 이벤트 저장
        // saveOutboxEvent(savedWaiting, WaitingEventType.WAITING_ENTERED);

        return WaitingEnterResponse.from(savedWaiting);
    }

    // 웨이팅 미입장 처리
    @Transactional
    public WaitingNoShowResponse noShowWaiting(UUID userId, UUID waitingId, WaitingNoShowRequest request) {
        // TODO: Store Service 내부 API 연동 후 요청 userId가 waitingId의 storeId 소유자인지 검증 추가
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new WaitingException(WaitingErrorCode.WAITING_NOT_FOUND));

        waiting.noShow(request.getReason());
        Waiting savedWaiting = waitingRepository.save(waiting);
        scheduleQueueRemovalAfterCommit(savedWaiting);
        // TODO: Kafka Outbox Publisher 도입 시 WAITING_NO_SHOW 이벤트 저장
        // saveOutboxEvent(savedWaiting, WaitingEventType.WAITING_NO_SHOW);

        return WaitingNoShowResponse.from(savedWaiting);
    }

    // 순번 임박 알림 대상 조회
    public List<NearTurnWaitingResponse> getNearTurnWaitings(UUID storeId, int threshold) {
        List<UUID> waitingIds = waitingQueueRedisStore.findNearTurn(storeId, threshold);
        if (waitingIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, Waiting> waitingsById = waitingRepository.findAllById(waitingIds).stream()
                .collect(Collectors.toMap(Waiting::getId, Function.identity()));

        return IntStream.range(0, waitingIds.size())
                .mapToObj(index -> {
                    UUID waitingId = waitingIds.get(index);
                    Waiting waiting = waitingsById.get(waitingId);
                    if (waiting == null) {
                        return null;
                    }
                    return NearTurnWaitingResponse.of(waiting, (long) index + 1);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    // 웨이팅 가능 여부 판단 - 가게 웨이팅 활성화 여부
    private void validateWaitingEnabled(StoreWaitingValues storeWaitingValues) {
        if (!Boolean.TRUE.equals(storeWaitingValues.waitingEnabled())) {
            throw new WaitingException(WaitingErrorCode.WAITING_DISABLED);
        }
    }

    // cancel/call/enter/no-show처럼 DB 상태를 먼저 바꾸는 흐름에서 사용
    // 트랜잭션이 정상 커밋된 뒤에만 Redis 대기열을 제거
    private void scheduleQueueRemovalAfterCommit(Waiting waiting) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            waitingQueueRedisStore.remove(waiting.getStoreId(), waiting.getUserId(), waiting.getId());
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    waitingQueueRedisStore.remove(waiting.getStoreId(), waiting.getUserId(), waiting.getId());
                } catch (RuntimeException e) {
                    log.warn("Failed to cleanup waiting queue after commit. storeId={}, userId={}, waitingId={}",
                            waiting.getStoreId(), waiting.getUserId(), waiting.getId(), e);
                }
            }
        });
    }

    // 웨이팅 등록용
    // DB 저장 실패 -> Redis 등록 롤백
    private Waiting saveWaitingWithQueueRollback(UUID storeId, Waiting waiting) {
        scheduleQueueRollbackOnTransactionFailure(storeId, waiting);
        try {
            return waitingRepository.saveAndFlush(waiting);
        } catch (RuntimeException e) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                rollbackQueueRegistrationSafely(storeId, waiting, e);
            }
            throw e;
        }
    }

    // 웨이팅 등록용 롤백 훅 예약
    // 트랜잭션 롤백 시 Redis 등록도 함께 롤백
    private void scheduleQueueRollbackOnTransactionFailure(UUID storeId, Waiting waiting) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    rollbackQueueRegistrationSafely(storeId, waiting, null);
                }
            }
        });
    }

    // 웨이팅 등록 롤백 즉시 실행
    // 대기열과 active user 키를 함께 제거
    private void rollbackQueueRegistrationSafely(UUID storeId, Waiting waiting, RuntimeException originalException) {
        try {
            waitingQueueRedisStore.remove(storeId, waiting.getUserId(), waiting.getId());
        } catch (RuntimeException rollbackException) {
            log.warn("Failed to rollback waiting queue. storeId={}, userId={}, waitingId={}",
                    storeId, waiting.getUserId(), waiting.getId(), rollbackException);
            if (originalException != null) {
                originalException.addSuppressed(rollbackException);
            }
        }
    }

    // 권한 확인 - 본인 or 마스터
    private void validateWaitingAccess(UUID userId, String role, Waiting waiting) {
        if (RoleAuthorizationUtils.hasAnyRole(role, AuthConstants.MASTER)) {
            return;
        }
        if (!waiting.getUserId().equals(userId)) {
            throw new BaseException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    // 사용자 취소 가능 여부 확인
    private void validateUserCancelable(String role, Waiting waiting) {
        if (!RoleAuthorizationUtils.hasAnyRole(role, AuthConstants.USER)) {
            return;
        }
        StoreWaitingValues storeWaitingValues = waitingSettingService.getStoreWaitingValues(waiting.getStoreId());
        if (!Boolean.TRUE.equals(storeWaitingValues.allowUserCancel())) {
            throw new WaitingException(WaitingErrorCode.WAITING_CANCEL_DISABLED);
        }
    }

    // 현재 순위
    private Long resolveCurrentRank(Waiting waiting) {
        if (!isQueueTrackedStatus(waiting.getStatus())) {
            return null;
        }
        return waitingQueueRedisStore.getRank(waiting.getStoreId(), waiting.getId());
    }

    // 현재 순위를 조회할 수 있는 진행 중 상태인지 확인
    private boolean isQueueTrackedStatus(WaitingStatus status) {
        return status == WaitingStatus.WAITING;
    }

    // 아웃박스 테이블 저장
    private void saveOutboxEvent(Waiting waiting, WaitingEventType eventType) {
        WaitingOutboxEvent outboxEvent = WaitingOutboxEvent.builder()
                .waiting(waiting)
                .eventType(eventType)
                .payload(createPayload(waiting, eventType))
                .build();
        waitingOutboxEventRepository.save(outboxEvent);
    }

    // Outbox 이벤트 payload 생성
    private String createPayload(Waiting waiting, WaitingEventType eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType.name());
        payload.put("waitingId", waiting.getId());
        payload.put("storeId", waiting.getStoreId());
        payload.put("userId", waiting.getUserId());
        payload.put("waitingNumber", waiting.getWaitingNumber());
        payload.put("peopleCount", waiting.getPeopleCount());
        payload.put("status", waiting.getStatus().name());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new WaitingException(WaitingErrorCode.WAITING_EVENT_PAYLOAD_SERIALIZE_FAILED);
        }
    }

}
