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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationReminderScheduler {

    private static final String SCHEDULER_LOCK_KEY = "reservation:scheduler:reminder";
    private static final String REMINDER_1DAY_PREFIX = "reminder:1day:";
    private static final String REMINDER_1HOUR_PREFIX = "reminder:1hour:";

    private final ReservationRepository reservationRepository;
    private final ReservationOutboxEventRepository outboxEventRepository;
    private final RedissonClient redissonClient;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedRate = 60000)
    public void sendReminders() {
        RLock lock = redissonClient.getLock(SCHEDULER_LOCK_KEY);
        try {
            if (!lock.tryLock(0, 60, java.util.concurrent.TimeUnit.SECONDS)) {
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            send1DayReminders(now);
            send1HourReminders(now);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("리마인더 스케줄러 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void send1DayReminders(LocalDateTime now) {
        List<Reservation> targets = reservationRepository.findByStatusAndScheduledAtBetweenAndDeletedAtIsNull(
                ReservationStatus.CONFIRMED, now.plusHours(23), now.plusHours(24));

        log.info("1일 전 리마인더 대상: {}건", targets.size());

        for (Reservation reservation : targets) {
            sendIfNotSent(reservation, EventType.RESERVATION_REMINDER_1DAY,
                    REMINDER_1DAY_PREFIX + reservation.getReservationId(), 25);
        }
    }

    private void send1HourReminders(LocalDateTime now) {
        List<Reservation> targets = reservationRepository.findByStatusAndScheduledAtBetweenAndDeletedAtIsNull(
                ReservationStatus.CONFIRMED, now.plusMinutes(50), now.plusMinutes(60));

        log.info("1시간 전 리마인더 대상: {}건", targets.size());

        for (Reservation reservation : targets) {
            sendIfNotSent(reservation, EventType.RESERVATION_REMINDER_1HOUR,
                    REMINDER_1HOUR_PREFIX + reservation.getReservationId(), 2);
        }
    }

    private void sendIfNotSent(Reservation reservation, EventType eventType, String redisKey, long ttlHours) {
        RBucket<String> bucket = redissonClient.getBucket(redisKey);
        if (!bucket.setIfAbsent("1", Duration.ofHours(ttlHours))) {
            log.debug("리마인더 이미 발송됨 - reservationId: {}, type: {}",
                    reservation.getReservationId(), eventType);
            return;
        }

        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                outboxEventRepository.save(buildOutboxEvent(reservation, eventType));
                return null;
            });
        } catch (Exception e) {
            log.error("리마인더 Outbox 저장 실패 - reservationId: {}, type: {}",
                    reservation.getReservationId(), eventType, e);
            bucket.delete();
        }
    }

    private ReservationOutboxEvent buildOutboxEvent(Reservation reservation, EventType eventType) {
        UUID outboxEventId = UUID.randomUUID();
        try {
            Map<String, Object> payloadData = new LinkedHashMap<>();
            payloadData.put("reservationId", reservation.getReservationId().toString());
            payloadData.put("userId", reservation.getUserId().toString());
            payloadData.put("storeId", reservation.getStoreId().toString());
            payloadData.put("storeName", reservation.getStoreName());
            payloadData.put("visitedAt", reservation.getScheduledAt());

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventId", outboxEventId.toString());
            envelope.put("eventType", eventType.name());
            envelope.put("schemaVersion", 1);
            envelope.put("occurredAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
            envelope.put("producer", "reservation-service");
            envelope.put("payload", payloadData);

            String payload = objectMapper.writeValueAsString(envelope);
            return ReservationOutboxEvent.builder()
                    .outboxEventId(outboxEventId)
                    .reservationId(reservation.getReservationId())
                    .eventType(eventType)
                    .payload(payload)
                    .build();
        } catch (JsonProcessingException e) {
            throw new BaseException(ReservationErrorCode.PAYLOAD_SERIALIZATION_FAILED);
        }
    }
}
