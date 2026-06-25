package com.omakase.kok.reservation.infrastructure.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.reservation.domain.entity.Reservation;
import com.omakase.kok.reservation.domain.entity.ReservationOutboxEvent;
import com.omakase.kok.reservation.domain.enums.EventType;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import com.omakase.kok.reservation.domain.exception.ReservationErrorCode;
import com.omakase.kok.reservation.domain.repository.ReservationOutboxEventRepository;
import com.omakase.kok.reservation.domain.repository.ReservationRepository;
import com.omakase.kok.reservation.infrastructure.client.PaymentFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingExpirationScheduler {

    private static final String SCHEDULER_LOCK_KEY = "reservation:scheduler:expire-pending";
    private static final String SLOT_CAPACITY_KEY = "slot:capacity:";

    private final ReservationRepository reservationRepository;
    private final ReservationOutboxEventRepository outboxEventRepository;
    private final PaymentFeignClient paymentFeignClient;
    private final RedissonClient redissonClient;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedRate = 30000)
    public void expirePendingReservations() {
        RLock lock = redissonClient.getLock(SCHEDULER_LOCK_KEY);
        try {
            if (!lock.tryLock(0, 30, TimeUnit.SECONDS)) {
                return;
            }

            LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
            List<Reservation> targets = reservationRepository
                    .findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(ReservationStatus.PAYMENT_PENDING, threshold);

            log.info("PENDING 만료 처리 대상: {}건", targets.size());

            for (Reservation reservation : targets) {
                try {
                    processExpiredReservation(reservation);
                } catch (Exception e) {
                    log.error("PENDING 예약 만료 처리 실패 - reservationId: {}", reservation.getReservationId(), e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("PENDING 만료 스케줄러 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void processExpiredReservation(Reservation reservation) {
        // Payment 만료 처리 (실패해도 예약 취소는 진행)
        try {
            var payment = paymentFeignClient.getPayment(reservation.getReservationId()).getData();
            paymentFeignClient.expire(payment.getPaymentId());
        } catch (Exception e) {
            log.warn("Payment 만료 처리 실패 - reservationId: {}", reservation.getReservationId(), e);
        }

        // 예약 취소 + Outbox 이벤트 저장 (실제 취소 여부를 반환)
        Boolean cancelled = new TransactionTemplate(transactionManager).execute(status -> {
            Reservation r = reservationRepository
                    .findByReservationIdAndDeletedAtIsNull(reservation.getReservationId())
                    .orElseThrow();
            if (r.getStatus() != ReservationStatus.PAYMENT_PENDING) {
                return false;
            }
            r.cancel("SYSTEM", "결제 시간 초과");
            outboxEventRepository.save(buildOutboxEvent(r));
            return true;
        });

        // 실제로 취소된 경우에만 Redis 잔여 인원 복구
        if (Boolean.TRUE.equals(cancelled)) {
            try {
                redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + reservation.getSlotId())
                        .addAndGet(reservation.getReservationSize());
            } catch (Exception e) {
                log.error("Redis 잔여 인원 복구 실패 - slotId: {}", reservation.getSlotId(), e);
            }
        }
    }

    private ReservationOutboxEvent buildOutboxEvent(Reservation reservation) {
        UUID outboxEventId = UUID.randomUUID();
        try {
            Map<String, Object> payloadData = new LinkedHashMap<>();
            payloadData.put("reservationId", reservation.getReservationId().toString());
            payloadData.put("userId", reservation.getUserId().toString());
            payloadData.put("storeId", reservation.getStoreId().toString());
            payloadData.put("storeName", reservation.getStoreName());
            payloadData.put("visitedAt", reservation.getScheduledAt());
            payloadData.put("cancelReason", reservation.getCancelReason());

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventId", outboxEventId.toString());
            envelope.put("eventType", EventType.RESERVATION_CANCELLED.name());
            envelope.put("schemaVersion", 1);
            envelope.put("occurredAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
            envelope.put("producer", "reservation-service");
            envelope.put("payload", payloadData);

            String payload = objectMapper.writeValueAsString(envelope);
            return ReservationOutboxEvent.builder()
                    .outboxEventId(outboxEventId)
                    .reservationId(reservation.getReservationId())
                    .eventType(EventType.RESERVATION_CANCELLED)
                    .payload(payload)
                    .build();
        } catch (JsonProcessingException e) {
            throw new BaseException(ReservationErrorCode.PAYLOAD_SERIALIZATION_FAILED);
        }
    }
}
