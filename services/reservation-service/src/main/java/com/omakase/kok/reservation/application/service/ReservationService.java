package com.omakase.kok.reservation.application.service;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.reservation.application.dto.CreateReservationRequest;
import com.omakase.kok.reservation.application.dto.ReservationResponse;
import com.omakase.kok.reservation.domain.entity.Reservation;
import com.omakase.kok.reservation.domain.entity.ReservationOutboxEvent;
import com.omakase.kok.reservation.domain.enums.EventType;
import com.omakase.kok.reservation.domain.enums.SlotStatus;
import com.omakase.kok.reservation.domain.exception.ReservationErrorCode;
import com.omakase.kok.reservation.domain.exception.SlotErrorCode;
import com.omakase.kok.reservation.domain.repository.ReservationOutboxEventRepository;
import com.omakase.kok.reservation.domain.repository.ReservationRepository;
import com.omakase.kok.reservation.domain.repository.ReservationSlotRepository;
import com.omakase.kok.reservation.infrastructure.client.PaymentFeignClient;
import com.omakase.kok.reservation.infrastructure.client.dto.CreatePaymentRequest;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
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
        var slot = slotRepository.findBySlotIdAndDeletedAtIsNull(request.getSlotId())
                .orElseThrow(() -> new BaseException(SlotErrorCode.SLOT_NOT_FOUND));

        if (slot.getStatus() != SlotStatus.OPEN) {
            throw new BaseException(ReservationErrorCode.SLOT_UNAVAILABLE);
        }

        RLock lock = redissonClient.getLock(SLOT_LOCK_KEY + request.getSlotId());
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new BaseException(ReservationErrorCode.SLOT_LOCK_FAILED);
            }

            RAtomicLong capacityKey = redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + request.getSlotId());
            long remaining = capacityKey.addAndGet(-request.getReservationSize());

            if (remaining < 0) {
                capacityKey.addAndGet(request.getReservationSize());
                throw new BaseException(ReservationErrorCode.SLOT_CAPACITY_EXCEEDED);
            }

            if (!slot.isDepositRequired()) {
                return new TransactionTemplate(transactionManager).execute(status -> {
                    Reservation reservation = buildReservation(request, userId, slot.getStoreId());
                    reservation.confirm();
                    reservationRepository.save(reservation);
                    outboxEventRepository.save(buildOutboxEvent(reservation, EventType.RESERVATION_CONFIRMED));
                    return ReservationResponse.from(reservation);
                });
            }

            UUID reservationId = new TransactionTemplate(transactionManager).execute(status -> {
                Reservation reservation = buildReservation(request, userId, slot.getStoreId());
                reservationRepository.save(reservation);
                return reservation.getReservationId();
            });

            try {
                paymentFeignClient.createPayment(new CreatePaymentRequest(
                        reservationId,
                        slot.getDepositAmount(),
                        request.getPaymentMethod()
                ));

                return new TransactionTemplate(transactionManager).execute(status -> {
                    Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
                    reservation.confirm();
                    outboxEventRepository.save(buildOutboxEvent(reservation, EventType.RESERVATION_CONFIRMED));
                    return ReservationResponse.from(reservation);
                });

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

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException(ReservationErrorCode.SLOT_LOCK_FAILED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
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
