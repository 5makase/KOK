package com.omakase.kok.reservation.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.reservation.application.dto.ChangeReservationRequest;
import com.omakase.kok.reservation.application.dto.CreateReservationRequest;
import com.omakase.kok.reservation.application.dto.ReservationResponse;
import com.omakase.kok.reservation.domain.entity.Reservation;
import com.omakase.kok.reservation.domain.entity.ReservationOutboxEvent;
import com.omakase.kok.reservation.domain.entity.ReservationSlot;
import com.omakase.kok.reservation.domain.enums.EventType;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import com.omakase.kok.reservation.domain.enums.SlotStatus;
import com.omakase.kok.reservation.domain.exception.ReservationErrorCode;
import com.omakase.kok.reservation.domain.exception.SlotErrorCode;
import com.omakase.kok.reservation.domain.repository.ReservationOutboxEventRepository;
import com.omakase.kok.reservation.domain.repository.ReservationRepository;
import com.omakase.kok.reservation.domain.repository.ReservationSlotRepository;
import com.omakase.kok.reservation.infrastructure.client.PaymentFeignClient;
import com.omakase.kok.reservation.application.dto.CancelReservationRequest;
import com.omakase.kok.reservation.infrastructure.client.dto.CreatePaymentRequest;
import com.omakase.kok.reservation.infrastructure.client.dto.PaymentResponse;
import com.omakase.kok.reservation.infrastructure.client.dto.RefundRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private static final String SLOT_CAPACITY_KEY = "slot:capacity:";
    private static final String SLOT_LOCK_KEY = "reservation:lock:slot:";

    private final ReservationRepository reservationRepository;
    private final ReservationSlotRepository slotRepository;
    private final ReservationOutboxEventRepository outboxEventRepository;
    private final RedissonClient redissonClient;
    private final PaymentFeignClient paymentFeignClient;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ReservationResponse createReservation(CreateReservationRequest request, UUID userId) {
        // 락 획득 전 빠른 사전 검사 (최적화용)
        var preCheckSlot = slotRepository.findBySlotIdAndDeletedAtIsNull(request.getSlotId())
                .orElseThrow(() -> new BaseException(SlotErrorCode.SLOT_NOT_FOUND));
        if (preCheckSlot.getStatus() != SlotStatus.OPEN) {
            throw new BaseException(ReservationErrorCode.SLOT_UNAVAILABLE);
        }

        RLock lock = redissonClient.getLock(SLOT_LOCK_KEY + request.getSlotId());
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new BaseException(ReservationErrorCode.SLOT_LOCK_FAILED);
            }

            // 락 획득 후 슬롯 상태 재검증 (레이스 컨디션 방지)
            var slot = slotRepository.findBySlotIdAndDeletedAtIsNull(request.getSlotId())
                    .orElseThrow(() -> new BaseException(SlotErrorCode.SLOT_NOT_FOUND));
            if (slot.getStatus() != SlotStatus.OPEN) {
                throw new BaseException(ReservationErrorCode.SLOT_UNAVAILABLE);
            }

            // 예약금 필요 슬롯은 결제 수단 필수
            if (slot.isDepositRequired() &&
                    (request.getPaymentMethod() == null || request.getPaymentMethod().isBlank())) {
                throw new BaseException(ReservationErrorCode.PAYMENT_METHOD_REQUIRED);
            }

            RAtomicLong capacityKey = redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + request.getSlotId());
            long remaining = capacityKey.addAndGet(-request.getReservationSize());

            if (remaining < 0) {
                capacityKey.addAndGet(request.getReservationSize());
                throw new BaseException(ReservationErrorCode.SLOT_CAPACITY_EXCEEDED);
            }

            if (!slot.isDepositRequired()) {
                try {
                    return new TransactionTemplate(transactionManager).execute(status -> {
                        Reservation reservation = buildReservation(request, userId, slot);
                        reservation.confirm();
                        reservationRepository.save(reservation);
                        ReservationSlot s = slotRepository.findBySlotIdAndDeletedAtIsNull(request.getSlotId()).orElseThrow();
                        s.decreaseCapacity(request.getReservationSize());
                        outboxEventRepository.save(buildOutboxEvent(reservation, EventType.RESERVATION_CONFIRMED));
                        return ReservationResponse.from(reservation);
                    });
                } catch (Exception e) {
                    capacityKey.addAndGet(request.getReservationSize());
                    throw e;
                }
            }

            UUID reservationId;
            try {
                reservationId = new TransactionTemplate(transactionManager).execute(status -> {
                    Reservation reservation = buildReservation(request, userId, slot);
                    reservationRepository.save(reservation);
                    return reservation.getReservationId();
                });
            } catch (Exception e) {
                capacityKey.addAndGet(request.getReservationSize());
                throw e;
            }

            try {
                paymentFeignClient.createPayment(new CreatePaymentRequest(
                        reservationId,
                        slot.getDepositAmount(),
                        request.getPaymentMethod()
                ));
            } catch (FeignException e) {
                capacityKey.addAndGet(request.getReservationSize());
                new TransactionTemplate(transactionManager).execute(status -> {
                    Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
                    reservation.cancel("SYSTEM", "결제 처리 실패");
                    outboxEventRepository.save(buildOutboxEvent(reservation, EventType.RESERVATION_CANCELLED));
                    return null;
                });
                if (e.status() >= 400 && e.status() < 500) {
                    log.warn("결제 서비스 클라이언트 오류 - status: {}, reservationId: {}", e.status(), reservationId);
                } else {
                    log.error("결제 서비스 서버 오류 - status: {}, reservationId: {}", e.status(), reservationId, e);
                }
                throw new BaseException(ReservationErrorCode.PAYMENT_FAILED);
            } catch (Exception e) {
                capacityKey.addAndGet(request.getReservationSize());
                new TransactionTemplate(transactionManager).execute(status -> {
                    Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
                    reservation.cancel("SYSTEM", "결제 처리 실패");
                    outboxEventRepository.save(buildOutboxEvent(reservation, EventType.RESERVATION_CANCELLED));
                    return null;
                });
                log.error("결제 처리 중 예외 발생 - reservationId: {}", reservationId, e);
                throw new BaseException(ReservationErrorCode.PAYMENT_FAILED);
            }

            // 결제 성공 후 확정 TX 실패 시 재시도 (일시적 DB 장애 대응)
            // 재시도 모두 실패 시 PAYMENT_PENDING 유지 → 스케줄러가 5분 내 expire + CANCELLED 처리
            RuntimeException lastException = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    return new TransactionTemplate(transactionManager).execute(status -> {
                        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
                        reservation.confirm();
                        ReservationSlot s = slotRepository.findBySlotIdAndDeletedAtIsNull(reservation.getSlotId()).orElseThrow();
                        s.decreaseCapacity(reservation.getReservationSize());
                        outboxEventRepository.save(buildOutboxEvent(reservation, EventType.RESERVATION_CONFIRMED));
                        return ReservationResponse.from(reservation);
                    });
                } catch (RuntimeException e) {
                    lastException = e;
                }
            }
            throw lastException;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException(ReservationErrorCode.SLOT_LOCK_FAILED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public List<ReservationResponse> getMyReservations(UUID userId) {
        return reservationRepository.findByUserIdAndDeletedAtIsNull(userId).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    public ReservationResponse getMyReservation(UUID reservationId, UUID userId) {
        Reservation reservation = reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId)
                .orElseThrow(() -> new BaseException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getUserId().equals(userId)) {
            throw new BaseException(ReservationErrorCode.RESERVATION_FORBIDDEN);
        }

        return ReservationResponse.from(reservation);
    }

    public List<ReservationResponse> getStoreReservations(UUID storeId) {
        return reservationRepository.findByStoreIdAndDeletedAtIsNull(storeId).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    public ReservationResponse getStoreReservation(UUID storeId, UUID reservationId) {
        Reservation reservation = reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId)
                .orElseThrow(() -> new BaseException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getStoreId().equals(storeId)) {
            throw new BaseException(ReservationErrorCode.RESERVATION_NOT_FOUND);
        }

        return ReservationResponse.from(reservation);
    }

    @Transactional
    public ReservationResponse visitReservation(UUID storeId, UUID reservationId) {
        Reservation reservation = reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId)
                .orElseThrow(() -> new BaseException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getStoreId().equals(storeId)) {
            throw new BaseException(ReservationErrorCode.RESERVATION_NOT_FOUND);
        }

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BaseException(ReservationErrorCode.RESERVATION_NOT_VISITABLE);
        }

        reservation.visit();
        outboxEventRepository.save(buildOutboxEvent(reservation, EventType.RESERVATION_VISITED));

        return ReservationResponse.from(reservation);
    }

    @Transactional
    public ReservationResponse noShowReservation(UUID storeId, UUID reservationId) {
        Reservation reservation = reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId)
                .orElseThrow(() -> new BaseException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getStoreId().equals(storeId)) {
            throw new BaseException(ReservationErrorCode.RESERVATION_NOT_FOUND);
        }

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BaseException(ReservationErrorCode.RESERVATION_NOT_VISITABLE);
        }

        reservation.noShow();
        outboxEventRepository.save(buildOutboxEvent(reservation, EventType.RESERVATION_NO_SHOW));

        return ReservationResponse.from(reservation);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ReservationResponse cancelReservation(UUID reservationId, UUID userId, CancelReservationRequest request) {
        Reservation reservation = reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId)
                .orElseThrow(() -> new BaseException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getUserId().equals(userId)) {
            throw new BaseException(ReservationErrorCode.RESERVATION_FORBIDDEN);
        }

        if (!reservation.isCancellable()) {
            throw new BaseException(ReservationErrorCode.RESERVATION_NOT_CANCELLABLE);
        }

        ReservationSlot slot = slotRepository.findBySlotIdAndDeletedAtIsNull(reservation.getSlotId())
                .orElseThrow(() -> new BaseException(SlotErrorCode.SLOT_NOT_FOUND));

        // 실제 결제 금액 조회 (환불 기준은 슬롯 설정이 아닌 실제 결제 금액)
        PaymentResponse payment = null;
        if (slot.isDepositRequired()) {
            payment = paymentFeignClient.getPayment(reservation.getReservationId()).getData();
        }
        final PaymentResponse finalPayment = payment;

        // DB 커밋 전 재검증으로 동시 취소 요청 방어
        String cancelReason = request != null ? request.getCancelReason() : null;

        RLock lock = redissonClient.getLock(SLOT_LOCK_KEY + reservation.getSlotId());
        ReservationResponse response;
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new BaseException(ReservationErrorCode.SLOT_LOCK_FAILED);
            }
            response = new TransactionTemplate(transactionManager).execute(status -> {
                Reservation r = reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId).orElseThrow();
                if (!r.isCancellable()) {
                    throw new BaseException(ReservationErrorCode.RESERVATION_NOT_CANCELLABLE);
                }
                r.cancel("USER", cancelReason);
                ReservationSlot s = slotRepository.findBySlotIdAndDeletedAtIsNull(r.getSlotId()).orElseThrow();
                s.increaseCapacity(r.getReservationSize());
                outboxEventRepository.save(buildOutboxEvent(r, EventType.RESERVATION_CANCELLED));
                return ReservationResponse.from(r);
            });
            try {
                redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + reservation.getSlotId())
                        .addAndGet(reservation.getReservationSize());
            } catch (Exception e) {
                log.error("Redis 잔여 인원 복구 실패 - slotId: {}", reservation.getSlotId(), e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException(ReservationErrorCode.SLOT_LOCK_FAILED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        // DB 커밋 이후 환불 처리 (DB가 취소 상태의 원천)
        if (slot.isDepositRequired() && finalPayment != null) {
            long refundAmount = calculateUserRefundAmount(slot.getSlotDate(), finalPayment.getAmount());
            if (refundAmount > 0) {
                try {
                    paymentFeignClient.refund(finalPayment.getPaymentId(), new RefundRequest(refundAmount));
                } catch (Exception e) {
                    log.error("환불 처리 실패 - reservationId: {}, paymentId: {}", reservationId, finalPayment.getPaymentId(), e);
                }
            }
        }

        return response;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ReservationResponse cancelByStore(UUID storeId, UUID reservationId, CancelReservationRequest request) {
        Reservation reservation = reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId)
                .orElseThrow(() -> new BaseException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getStoreId().equals(storeId)) {
            throw new BaseException(ReservationErrorCode.RESERVATION_NOT_FOUND);
        }

        if (!reservation.isCancellable()) {
            throw new BaseException(ReservationErrorCode.RESERVATION_NOT_CANCELLABLE);
        }

        ReservationSlot slot = slotRepository.findBySlotIdAndDeletedAtIsNull(reservation.getSlotId())
                .orElseThrow(() -> new BaseException(SlotErrorCode.SLOT_NOT_FOUND));

        // 실제 결제 금액 조회 (점주 취소 시 전액 환불 기준)
        PaymentResponse payment = null;
        if (slot.isDepositRequired()) {
            payment = paymentFeignClient.getPayment(reservation.getReservationId()).getData();
        }
        final PaymentResponse finalPayment = payment;

        // DB 커밋 전 재검증으로 동시 취소 요청 방어
        String cancelReason = request != null ? request.getCancelReason() : null;

        RLock lock = redissonClient.getLock(SLOT_LOCK_KEY + reservation.getSlotId());
        ReservationResponse response;
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new BaseException(ReservationErrorCode.SLOT_LOCK_FAILED);
            }
            response = new TransactionTemplate(transactionManager).execute(status -> {
                Reservation r = reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId).orElseThrow();
                if (!r.isCancellable()) {
                    throw new BaseException(ReservationErrorCode.RESERVATION_NOT_CANCELLABLE);
                }
                r.cancel("OWNER", cancelReason);
                ReservationSlot s = slotRepository.findBySlotIdAndDeletedAtIsNull(r.getSlotId()).orElseThrow();
                s.increaseCapacity(r.getReservationSize());
                outboxEventRepository.save(buildOutboxEvent(r, EventType.RESERVATION_CANCELLED));
                return ReservationResponse.from(r);
            });
            try {
                redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + reservation.getSlotId())
                        .addAndGet(reservation.getReservationSize());
            } catch (Exception e) {
                log.error("Redis 잔여 인원 복구 실패 - slotId: {}", reservation.getSlotId(), e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException(ReservationErrorCode.SLOT_LOCK_FAILED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        // DB 커밋 이후 전액 환불 처리
        if (slot.isDepositRequired() && finalPayment != null) {
            try {
                paymentFeignClient.refund(finalPayment.getPaymentId(), new RefundRequest(finalPayment.getAmount()));
            } catch (Exception e) {
                log.error("환불 처리 실패 - reservationId: {}, paymentId: {}", reservationId, finalPayment.getPaymentId(), e);
            }
        }

        return response;
    }

    private long calculateUserRefundAmount(LocalDate slotDate, Long depositAmount) {
        long daysUntilVisit = ChronoUnit.DAYS.between(LocalDate.now(), slotDate);
        if (daysUntilVisit >= 3) {
            return depositAmount;
        } else if (daysUntilVisit >= 1) {
            return depositAmount / 2;
        }
        return 0;
    }

    private Reservation buildReservation(CreateReservationRequest request, UUID userId, ReservationSlot slot) {
        return Reservation.builder()
                .slotId(request.getSlotId())
                .userId(userId)
                .storeId(slot.getStoreId())
                .storeName(slot.getStoreName())
                .scheduledAt(LocalDateTime.of(slot.getSlotDate(), slot.getSlotTime()))
                .bookerName(request.getBookerName())
                .bookerPhone(request.getBookerPhone())
                .reservationSize(request.getReservationSize())
                .requestMessage(request.getRequestMessage())
                .build();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ReservationResponse changeReservation(UUID reservationId, UUID userId, ChangeReservationRequest request) {
        Reservation reservation = reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId)
                .orElseThrow(() -> new BaseException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getUserId().equals(userId)) {
            throw new BaseException(ReservationErrorCode.RESERVATION_FORBIDDEN);
        }

        int newSize = request.getReservationSize();

        // 슬롯 락을 통해 동시 요청 간 Redis 잔여 인원 불일치 방지
        RLock lock = redissonClient.getLock(SLOT_LOCK_KEY + reservation.getSlotId());
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new BaseException(ReservationErrorCode.SLOT_LOCK_FAILED);
            }

            // 락 내에서 fresh row 재조회 → 동시 변경 시 sizeDiff가 stale해지는 문제 방지
            Reservation fresh = reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId)
                    .orElseThrow(() -> new BaseException(ReservationErrorCode.RESERVATION_NOT_FOUND));

            // CONFIRMED 상태 및 당일 변경 금지 검증을 sizeDiff 계산보다 먼저 수행
            if (fresh.getStatus() != ReservationStatus.CONFIRMED) {
                throw new BaseException(ReservationErrorCode.RESERVATION_NOT_CHANGEABLE);
            }

            int sizeDiff = newSize - fresh.getReservationSize();

            if (sizeDiff == 0) {
                return ReservationResponse.from(fresh);
            }

            RAtomicLong capacityKey = redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + fresh.getSlotId());
            long remaining = capacityKey.addAndGet(-sizeDiff);
            if (remaining < 0) {
                capacityKey.addAndGet(sizeDiff);
                throw new BaseException(ReservationErrorCode.SLOT_CAPACITY_EXCEEDED);
            }

            try {
                return new TransactionTemplate(transactionManager).execute(status -> {
                    Reservation r = reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId)
                            .orElseThrow();
                    r.change(newSize);
                    ReservationSlot s = slotRepository.findBySlotIdAndDeletedAtIsNull(r.getSlotId()).orElseThrow();
                    if (sizeDiff > 0) {
                        s.decreaseCapacity(sizeDiff);
                    } else {
                        s.increaseCapacity(-sizeDiff);
                    }
                    outboxEventRepository.save(buildOutboxEvent(r, EventType.RESERVATION_CHANGED));
                    return ReservationResponse.from(r);
                });
            } catch (Exception e) {
                try {
                    redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + fresh.getSlotId()).addAndGet(sizeDiff);
                } catch (Exception redisEx) {
                    log.error("Redis 잔여 인원 복구 실패 - slotId: {}", fresh.getSlotId(), redisEx);
                }
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException(ReservationErrorCode.SLOT_LOCK_FAILED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private ReservationOutboxEvent buildOutboxEvent(Reservation reservation, EventType eventType) {
        UUID outboxEventId = UUID.randomUUID();
        try {
            LocalDateTime visitedAtValue = (eventType == EventType.RESERVATION_VISITED)
                    ? reservation.getVisitedAt()
                    : reservation.getScheduledAt();

            Map<String, Object> payloadData = new LinkedHashMap<>();
            payloadData.put("reservationId", reservation.getReservationId().toString());
            payloadData.put("userId", reservation.getUserId().toString());
            payloadData.put("storeId", reservation.getStoreId().toString());
            payloadData.put("storeName", reservation.getStoreName());
            payloadData.put("visitedAt", visitedAtValue);

            if (eventType == EventType.RESERVATION_CONFIRMED || eventType == EventType.RESERVATION_CHANGED) {
                payloadData.put("partySize", reservation.getReservationSize());
            }
            if (eventType == EventType.RESERVATION_CANCELLED) {
                payloadData.put("cancelReason", reservation.getCancelReason());
            }

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventId", outboxEventId.toString());
            envelope.put("eventType", eventType.name());
            envelope.put("schemaVersion", 1);
            envelope.put("occurredAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
            envelope.put("producer", "reservation-service");
            envelope.put("payload", payloadData);

            String payload = objectMapper.writeValueAsString(envelope);
            return ReservationOutboxEvent.builder()
                    .reservationId(reservation.getReservationId())
                    .eventType(eventType)
                    .payload(payload)
                    .build();
        } catch (JsonProcessingException e) {
            throw new BaseException(ReservationErrorCode.PAYLOAD_SERIALIZATION_FAILED);
        }
    }
}
