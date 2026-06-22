package com.omakase.kok.reservation.application.service;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.reservation.application.dto.CreateReservationRequest;
import com.omakase.kok.reservation.application.dto.ReservationResponse;
import com.omakase.kok.reservation.domain.entity.Reservation;
import com.omakase.kok.reservation.domain.entity.ReservationOutboxEvent;
import com.omakase.kok.reservation.domain.entity.ReservationSlot;
import com.omakase.kok.reservation.domain.enums.EventType;
import com.omakase.kok.reservation.domain.enums.SlotStatus;
import com.omakase.kok.reservation.domain.exception.ReservationErrorCode;
import com.omakase.kok.reservation.domain.exception.SlotErrorCode;
import com.omakase.kok.reservation.domain.repository.ReservationOutboxEventRepository;
import com.omakase.kok.reservation.domain.repository.ReservationRepository;
import com.omakase.kok.reservation.domain.repository.ReservationSlotRepository;
import com.omakase.kok.reservation.infrastructure.client.PaymentFeignClient;
import com.omakase.kok.reservation.application.dto.CancelReservationRequest;
import com.omakase.kok.reservation.infrastructure.client.dto.CreatePaymentRequest;
import com.omakase.kok.reservation.infrastructure.client.dto.RefundRequest;
import lombok.RequiredArgsConstructor;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
                        Reservation reservation = buildReservation(request, userId, slot.getStoreId());
                        reservation.confirm();
                        reservationRepository.save(reservation);
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
                    Reservation reservation = buildReservation(request, userId, slot.getStoreId());
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
            } catch (Exception e) {
                capacityKey.addAndGet(request.getReservationSize());
                new TransactionTemplate(transactionManager).execute(status -> {
                    Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
                    reservation.cancel("SYSTEM", "결제 처리 실패");
                    outboxEventRepository.save(buildOutboxEvent(reservation, EventType.RESERVATION_CANCELLED));
                    return null;
                });
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

        if (slot.isDepositRequired()) {
            long refundAmount = calculateUserRefundAmount(slot.getSlotDate(), slot.getDepositAmount());
            if (refundAmount > 0) {
                var payment = paymentFeignClient.getPayment(reservation.getReservationId()).getData();
                paymentFeignClient.refund(payment.getPaymentId(), new RefundRequest(refundAmount));
            }
        }

        String cancelReason = request != null ? request.getCancelReason() : null;
        ReservationResponse response = new TransactionTemplate(transactionManager).execute(status -> {
            Reservation r = reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId).orElseThrow();
            r.cancel("USER", cancelReason);
            outboxEventRepository.save(buildOutboxEvent(r, EventType.RESERVATION_CANCELLED));
            return ReservationResponse.from(r);
        });

        redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + reservation.getSlotId())
                .addAndGet(reservation.getReservationSize());

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

        if (slot.isDepositRequired()) {
            var payment = paymentFeignClient.getPayment(reservation.getReservationId()).getData();
            paymentFeignClient.refund(payment.getPaymentId(), new RefundRequest(slot.getDepositAmount()));
        }

        String cancelReason = request != null ? request.getCancelReason() : null;
        ReservationResponse response = new TransactionTemplate(transactionManager).execute(status -> {
            Reservation r = reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId).orElseThrow();
            r.cancel("OWNER", cancelReason);
            outboxEventRepository.save(buildOutboxEvent(r, EventType.RESERVATION_CANCELLED));
            return ReservationResponse.from(r);
        });

        redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + reservation.getSlotId())
                .addAndGet(reservation.getReservationSize());

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

    private Reservation buildReservation(CreateReservationRequest request, UUID userId, UUID storeId) {
        return Reservation.builder()
                .slotId(request.getSlotId())
                .userId(userId)
                .storeId(storeId)
                .bookerName(request.getBookerName())
                .bookerPhone(request.getBookerPhone())
                .reservationSize(request.getReservationSize())
                .requestMessage(request.getRequestMessage())
                .build();
    }

    private ReservationOutboxEvent buildOutboxEvent(Reservation reservation, EventType eventType) {
        String payload = String.format(
                "{\"reservationId\":\"%s\",\"storeId\":\"%s\",\"userId\":\"%s\",\"status\":\"%s\",\"occurredAt\":\"%s\"}",
                reservation.getReservationId(), reservation.getStoreId(),
                reservation.getUserId(), reservation.getStatus(), LocalDateTime.now()
        );
        return ReservationOutboxEvent.builder()
                .reservationId(reservation.getReservationId())
                .eventType(eventType)
                .payload(payload)
                .build();
    }
}
