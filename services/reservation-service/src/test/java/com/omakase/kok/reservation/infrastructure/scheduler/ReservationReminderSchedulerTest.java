package com.omakase.kok.reservation.infrastructure.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omakase.kok.reservation.domain.entity.Reservation;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import com.omakase.kok.reservation.domain.repository.ReservationOutboxEventRepository;
import com.omakase.kok.reservation.domain.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationReminderScheduler 단위 테스트")
class ReservationReminderSchedulerTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationOutboxEventRepository outboxEventRepository;
    @Mock private RedissonClient redissonClient;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private RLock schedulerLock;
    @Mock private RBucket<String> reminderBucket;

    private ReservationReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        scheduler = new ReservationReminderScheduler(
                reservationRepository, outboxEventRepository,
                redissonClient, transactionManager, objectMapper);
    }

    private Reservation buildConfirmedReservation() {
        Reservation r = Reservation.builder()
                .slotId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .storeName("테스트 매장")
                .scheduledAt(LocalDateTime.now().plusHours(24))
                .bookerName("홍길동")
                .bookerPhone("010-1234-5678")
                .reservationSize(2)
                .build();
        ReflectionTestUtils.setField(r, "reservationId", UUID.randomUUID());
        r.confirm();
        return r;
    }

    private void givenLockAcquired() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(schedulerLock);
        when(schedulerLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(schedulerLock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Nested
    @DisplayName("sendReminders()")
    class SendReminders {

        @Test
        @DisplayName("리마인더가 이미 발송된 경우 setIfAbsent NX로 Outbox 이벤트를 저장하지 않는다")
        void skip_whenAlreadySent() throws Exception {
            Reservation reservation = buildConfirmedReservation();

            givenLockAcquired();
            when(reservationRepository.findByStatusAndScheduledAtBetweenAndDeletedAtIsNull(
                    eq(ReservationStatus.CONFIRMED), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of(reservation))
                    .thenReturn(List.of());

            doReturn(reminderBucket).when(redissonClient).getBucket(anyString());
            when(reminderBucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(false);

            scheduler.sendReminders();

            verify(outboxEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("CANCELLED 예약은 리마인더 Outbox를 저장하지 않고 Redis 키를 삭제한다")
        void skip_whenReservationCancelled() throws Exception {
            Reservation confirmed = buildConfirmedReservation();
            UUID reservationId = confirmed.getReservationId();

            givenLockAcquired();
            when(reservationRepository.findByStatusAndScheduledAtBetweenAndDeletedAtIsNull(
                    eq(ReservationStatus.CONFIRMED), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of(confirmed))
                    .thenReturn(List.of());

            doReturn(reminderBucket).when(redissonClient).getBucket(anyString());
            when(reminderBucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);

            Reservation cancelled = buildConfirmedReservation();
            ReflectionTestUtils.setField(cancelled, "reservationId", reservationId);
            cancelled.cancel("SYSTEM", "테스트 취소");

            TransactionStatus txStatus = mock(TransactionStatus.class);
            when(transactionManager.getTransaction(any())).thenReturn(txStatus);
            when(reservationRepository.findByReservationIdAndDeletedAtIsNull(reservationId))
                    .thenReturn(Optional.of(cancelled));

            scheduler.sendReminders();

            verify(outboxEventRepository, never()).save(any());
            verify(reminderBucket).delete();
        }
    }
}
