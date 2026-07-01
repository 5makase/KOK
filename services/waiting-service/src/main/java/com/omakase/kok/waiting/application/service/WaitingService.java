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
import com.omakase.kok.waiting.infrastructure.client.StoreSummaryReader;
import com.omakase.kok.waiting.infrastructure.client.dto.StoreSummaryResponse;
import com.omakase.kok.waiting.infrastructure.messaging.WaitingEventFactory;
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
import com.omakase.kok.waiting.presentation.dto.response.WaitingQueueRestoreResponse;
import com.omakase.kok.waiting.presentation.dto.response.WaitingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class WaitingService {
    // Lock
    private static final String WAITING_CALL_NEXT_LOCK_KEY_PREFIX = "waiting:store:";
    private static final String WAITING_CALL_NEXT_LOCK_KEY_SUFFIX = ":call-next:lock";
    private static final long WAITING_CALL_NEXT_LOCK_WAIT_SECONDS = 0L;

    // 자동 미입장
    private static final String AUTO_NO_SHOW_REASON = "호출 제한 시간 초과";
    private static final UUID SYSTEM_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final WaitingRepository waitingRepository;
    private final WaitingOutboxEventRepository waitingOutboxEventRepository;
    private final WaitingQueueRedisStore waitingQueueRedisStore;
    private final WaitingSettingService waitingSettingService;
    private final StoreSummaryReader storeSummaryReader;
    private final WaitingEventFactory waitingEventFactory;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;

    // 웨이팅 등록
    @Transactional
    public WaitingResponse createWaiting(UUID userId, WaitingCreateRequest request) {
        UUID storeId = request.getStoreId();
        // 본인 가게 검증
        StoreSummaryResponse storeSummary = getStoreSummary(storeId);
        validateNotOwnStore(userId, storeSummary);
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
                storeWaitingValues.maxWaitingCount(),
                LocalDate.now()
        ).orElseThrow(() -> new WaitingException(WaitingErrorCode.WAITING_CAPACITY_EXCEEDED));
        // 웨이팅 생성
        Waiting waiting = Waiting.builder()
                .id(waitingId)
                .storeId(storeId)
                .storeName(storeSummary.getStoreName())
                .userId(userId)
                .waitingNumber(registration.waitingNumber())
                .peopleCount(request.getPeopleCount())
                .requestMessage(request.getRequestMessage())
                .build();
        Waiting savedWaiting = saveWaitingWithQueueRollback(storeId, waiting);
        saveRegisteredOutboxEvent(savedWaiting, registration.currentRank());

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
            String role,
            UUID storeId,
            WaitingStatus status,
            Pageable pageable
    ) {
        validateStoreOwnerAccess(userId, role, storeId);
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
        saveOutboxEvent(savedWaiting, WaitingEventType.WAITING_CANCELLED);

        return WaitingCancelResponse.from(savedWaiting);
    }

    // 다음 순번 웨이팅 호출
    @Transactional
    public WaitingCallResponse callNextWaiting(UUID userId, String role, UUID storeId) {
        validateStoreOwnerAccess(userId, role, storeId);
        RLock lock = redissonClient.getLock(callNextLockKey(storeId));
        boolean unlockInFinally = false;
        try {
            // 이미 처리 중인 호출이 있으면 기다리지 않고 즉시 실패
            if (!lock.tryLock(
                    WAITING_CALL_NEXT_LOCK_WAIT_SECONDS,
                    TimeUnit.SECONDS
            )) {
                throw new WaitingException(WaitingErrorCode.WAITING_CALL_LOCK_FAILED);
            }
            // 트랜잭션 커밋/롤백이 끝난 뒤 락을 해제해 커밋 전 중복 진입을 방지
            unlockInFinally = registerLockReleaseAfterTransaction(lock);

            // 락 획득 후 최신 Redis 대기열 기준으로 호출 대상을 다시 조회
            UUID waitingId = waitingQueueRedisStore.findFirst(storeId)
                    .orElseThrow(() -> new WaitingException(WaitingErrorCode.WAITING_CALL_TARGET_NOT_FOUND));
            Waiting waiting = waitingRepository.findById(waitingId)
                    .orElseThrow(() -> new WaitingException(WaitingErrorCode.WAITING_CALL_TARGET_NOT_FOUND));

            waiting.validateCallAllowed();
            Integer callTimeoutMinutes = waitingSettingService.getStoreWaitingValues(storeId).callTimeoutMinutes();
            waiting.call(callTimeoutMinutes);
            Waiting savedWaiting = waitingRepository.save(waiting);
            scheduleQueueRemovalAfterCommit(savedWaiting);
            saveCalledOutboxEvent(savedWaiting, callTimeoutMinutes);

            return WaitingCallResponse.from(savedWaiting);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WaitingException(WaitingErrorCode.WAITING_CALL_LOCK_FAILED);
        } finally {
            if (unlockInFinally) {
                unlockSafely(lock);
            }
        }
    }

    // 웨이팅 입장 완료 처리
    @Transactional
    public WaitingEnterResponse enterWaiting(UUID userId, String role, UUID waitingId) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new WaitingException(WaitingErrorCode.WAITING_NOT_FOUND));
        validateStoreOwnerAccess(userId, role, waiting.getStoreId());

        waiting.enter();
        Waiting savedWaiting = waitingRepository.save(waiting);
        scheduleQueueRemovalAfterCommit(savedWaiting);
        saveOutboxEvent(savedWaiting, WaitingEventType.WAITING_ENTERED);

        return WaitingEnterResponse.from(savedWaiting);
    }

    // 웨이팅 미입장 처리
    @Transactional
    public WaitingNoShowResponse noShowWaiting(UUID userId, String role, UUID waitingId, WaitingNoShowRequest request) {
        Waiting waiting = waitingRepository.findById(waitingId)
                .orElseThrow(() -> new WaitingException(WaitingErrorCode.WAITING_NOT_FOUND));
        validateStoreOwnerAccess(userId, role, waiting.getStoreId());

        waiting.noShow(request.getReason());
        Waiting savedWaiting = waitingRepository.save(waiting);
        scheduleQueueRemovalAfterCommit(savedWaiting);
        saveOutboxEvent(savedWaiting, WaitingEventType.WAITING_NO_SHOW);

        return WaitingNoShowResponse.from(savedWaiting);
    }

    // 호출 제한 시간이 지난 웨이팅을 자동 미입장 처리
    @Transactional
    public int autoNoShowExpiredWaitings(int batchSize) {
        LocalDateTime now = LocalDateTime.now();
        List<UUID> expiredWaitingIds = waitingRepository.findExpiredCalledWaitingIds(
                WaitingStatus.CALLED,
                now,
                PageRequest.of(0, batchSize)
        );

        int processedCount = 0;
        for (UUID waitingId : expiredWaitingIds) {
            int updatedCount = waitingRepository.markNoShowIfExpired(
                    waitingId,
                    WaitingStatus.CALLED,
                    WaitingStatus.NO_SHOW,
                    AUTO_NO_SHOW_REASON,
                    SYSTEM_ACTOR_ID,
                    now
            );
            if (updatedCount != 1) {
                continue;
            }

            waitingRepository.findById(waitingId).ifPresent(waiting -> {
                scheduleQueueRemovalAfterCommit(waiting);
                saveOutboxEvent(waiting, WaitingEventType.WAITING_NO_SHOW);
            });
            processedCount++;
        }

        return processedCount;
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

    // DB 기준 Redis 대기열 복구
    public WaitingQueueRestoreResponse restoreWaitingQueue(UUID storeId, LocalDate waitingDate) {
        LocalDate targetDate = waitingDate != null ? waitingDate : LocalDate.now();
        List<Waiting> waitings = getWaitingQueueSnapshot(storeId, targetDate);
        try {
            waitingQueueRedisStore.restoreQueue(storeId, targetDate, waitings);
        } catch (RuntimeException e) {
            log.error("Failed to restore waiting queue from DB. storeId={}, waitingDate={}", storeId, targetDate, e);
            throw new WaitingException(WaitingErrorCode.WAITING_QUEUE_RESTORE_FAILED);
        }

        long maxWaitingNumber = waitings.stream()
                .mapToLong(Waiting::getWaitingNumber)
                .max()
                .orElse(0L);
        return WaitingQueueRestoreResponse.of(storeId, targetDate, waitings.size(), maxWaitingNumber);
    }

    /**
     * Validation
     */

    // 웨이팅 가능 여부 판단 - 가게 웨이팅 활성화 여부
    private void validateWaitingEnabled(StoreWaitingValues storeWaitingValues) {
        if (!Boolean.TRUE.equals(storeWaitingValues.waitingEnabled())) {
            throw new WaitingException(WaitingErrorCode.WAITING_DISABLED);
        }
    }

    // 본인 가게인지 확인(본인 가게 웨이팅 불가)
    private void validateNotOwnStore(UUID userId, StoreSummaryResponse storeSummary) {
        if (userId.equals(storeSummary.getOwnerId())) {
            throw new WaitingException(WaitingErrorCode.WAITING_OWN_STORE_NOT_ALLOWED);
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

    // 본인 가게 여부 확인
    private void validateStoreOwnerAccess(UUID userId, String role, UUID storeId) {
        if (RoleAuthorizationUtils.hasAnyRole(role, AuthConstants.MASTER)) {
            return;
        }
        StoreSummaryResponse storeSummary = getStoreSummary(storeId);
        if (!userId.equals(storeSummary.getOwnerId())) {
            throw new BaseException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    // 가게 내부 API 호출
    private StoreSummaryResponse getStoreSummary(UUID storeId) {
        return storeSummaryReader.getStoreSummary(storeId);
    }

    /**
     * Lock
     */

    // Redis 락 키
    private String callNextLockKey(UUID storeId) {
        return WAITING_CALL_NEXT_LOCK_KEY_PREFIX + storeId + WAITING_CALL_NEXT_LOCK_KEY_SUFFIX;
    }

    // 트랜잭션이 있으면 커밋/롤백 완료 후 락을 해제하고, 없으면 호출자가 finally에서 해제
    private boolean registerLockReleaseAfterTransaction(RLock lock) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return true;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                unlockSafely(lock);
            }
        });
        return false;
    }

    // Lock 해제
    private void unlockSafely(RLock lock) {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * Queue Sync
     */

    // DB 상태를 먼저 바꾸는 흐름에서 사용(cancel/call/enter/no-show)
    // 트랜잭션이 정상 커밋된 뒤에만 Redis 대기열을 제거
    private void scheduleQueueRemovalAfterCommit(Waiting waiting) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            waitingQueueRedisStore.remove(waiting.getStoreId(), waiting.getUserId(), waiting.getId(), waitingDate(waiting));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    waitingQueueRedisStore.remove(waiting.getStoreId(), waiting.getUserId(), waiting.getId(), waitingDate(waiting));
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

    // Redis 복구 기준이 되는 DB 대기열 스냅샷 조회
    private List<Waiting> getWaitingQueueSnapshot(UUID storeId, LocalDate waitingDate) {
        return waitingRepository.findByStoreIdAndStatusAndCreatedAtBetweenOrderByWaitingNumberAsc(
                storeId,
                WaitingStatus.WAITING,
                startOfDay(waitingDate),
                startOfNextDay(waitingDate)
        );
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
            waitingQueueRedisStore.remove(storeId, waiting.getUserId(), waiting.getId(), waitingDate(waiting));
        } catch (RuntimeException rollbackException) {
            log.warn("Failed to rollback waiting queue. storeId={}, userId={}, waitingId={}",
                    storeId, waiting.getUserId(), waiting.getId(), rollbackException);
            if (originalException != null) {
                originalException.addSuppressed(rollbackException);
            }
        }
    }

    /**
     * Rank
     */

    // 현재 순위
    private Long resolveCurrentRank(Waiting waiting) {
        if (!isQueueTrackedStatus(waiting.getStatus())) {
            return null;
        }
        return waitingQueueRedisStore.getRank(waiting.getStoreId(), waiting.getId(), waitingDate(waiting));
    }

    // 현재 순위를 조회할 수 있는 진행 중 상태인지 확인
    private boolean isQueueTrackedStatus(WaitingStatus status) {
        return status == WaitingStatus.WAITING;
    }

    private LocalDate waitingDate(Waiting waiting) {
        return waiting.getCreatedAt() != null ? waiting.getCreatedAt().toLocalDate() : LocalDate.now();
    }

    // 조회 시작 시각: 해당 날짜 00:00
    private LocalDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay();
    }

    // 조회 종료 시각: 다음 날짜 00:00
    private LocalDateTime startOfNextDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay();
    }

    /**
     * Outbox
     */

    // 웨이팅 등록 이벤트 저장
    private void saveRegisteredOutboxEvent(Waiting waiting, Long currentRank) {
        Object envelope = waitingEventFactory.createRegisteredEnvelope(UUID.randomUUID(), waiting, currentRank);
        saveOutboxEvent(waiting, WaitingEventType.WAITING_REGISTERED, envelope);
    }

    // 웨이팅 호출 이벤트 저장
    private void saveCalledOutboxEvent(Waiting waiting, Integer callTimeoutMinutes) {
        Object envelope = waitingEventFactory.createCalledEnvelope(UUID.randomUUID(), waiting, callTimeoutMinutes);
        saveOutboxEvent(waiting, WaitingEventType.WAITING_CALLED, envelope);
    }

    // 아웃박스 테이블 저장
    private void saveOutboxEvent(Waiting waiting, WaitingEventType eventType) {
        Object envelope = switch (eventType) {
            case WAITING_ENTERED -> waitingEventFactory.createEnteredEnvelope(UUID.randomUUID(), waiting);
            case WAITING_CANCELLED -> waitingEventFactory.createCancelledEnvelope(UUID.randomUUID(), waiting);
            case WAITING_NO_SHOW -> waitingEventFactory.createNoShowEnvelope(UUID.randomUUID(), waiting);
            default -> throw new IllegalArgumentException("Unsupported waiting event type: " + eventType);
        };
        saveOutboxEvent(waiting, eventType, envelope);
    }

    // 아웃박스 테이블 저장
    private void saveOutboxEvent(Waiting waiting, WaitingEventType eventType, Object envelope) {
        WaitingOutboxEvent outboxEvent = WaitingOutboxEvent.builder()
                .waiting(waiting)
                .eventType(eventType)
                .payload(serializeEnvelope(envelope))
                .build();
        waitingOutboxEventRepository.save(outboxEvent);
    }

    // Outbox 이벤트 직렬화
    private String serializeEnvelope(Object envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new WaitingException(WaitingErrorCode.WAITING_EVENT_PAYLOAD_SERIALIZE_FAILED);
        }
    }

}
