package com.omakase.kok.reservation.application.service;

import com.omakase.kok.reservation.application.dto.CreateReservationRequest;
import com.omakase.kok.reservation.domain.entity.ReservationSlot;
import com.omakase.kok.reservation.domain.repository.ReservationOutboxEventRepository;
import com.omakase.kok.reservation.domain.repository.ReservationRepository;
import com.omakase.kok.reservation.domain.repository.ReservationSlotRepository;
import com.omakase.kok.reservation.infrastructure.client.PaymentFeignClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"reservation.events.v1"})
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@DisplayName("예약 동시성 통합 테스트")
class ReservationConcurrencyIntegrationTest {

    private static final String SLOT_CAPACITY_KEY = "slot:capacity:";

    @Autowired private ReservationService reservationService;
    @Autowired private ReservationSlotRepository slotRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationOutboxEventRepository outboxEventRepository;
    @Autowired private RedissonClient redissonClient;

    @MockBean private PaymentFeignClient paymentFeignClient;

    private ReservationSlot slot;

    @BeforeEach
    void setUp() {
        slot = ReservationSlot.builder()
                .storeId(UUID.randomUUID())
                .storeName("테스트 매장")
                .slotDate(LocalDate.now().plusDays(5))
                .slotTime(LocalTime.of(18, 0))
                .maxCapacity(4)
                .depositRequired(false)
                .depositAmount(null)
                .build();
        slotRepository.save(slot);
        redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slot.getSlotId()).set(4);
    }

    @AfterEach
    void tearDown() {
        outboxEventRepository.deleteAll();
        reservationRepository.deleteAll();
        slotRepository.deleteAll();
        redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slot.getSlotId()).delete();
    }

    @Test
    @DisplayName("최대 인원 4명 슬롯에 10명이 동시 예약 요청해도 실제 예약은 4명을 초과하지 않는다")
    void concurrentBooking_doesNotExceedMaxCapacity() throws InterruptedException {
        int threadCount = 10;
        int maxCapacity = 4;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    CreateReservationRequest request = buildRequest(
                            slot.getSlotId(), 1, "예약자" + idx, "010-0000-00" + String.format("%02d", idx));
                    ready.countDown();
                    start.await();
                    reservationService.createReservation(request, UUID.randomUUID());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();  // 모든 스레드 준비 완료 대기
        start.countDown();  // 동시 시작
        done.await();  // 모든 스레드 완료 대기
        executor.shutdown();

        // 성공한 예약은 최대 인원을 초과할 수 없음
        assertThat(successCount.get())
                .as("성공한 예약 수는 최대 인원(%d)을 초과할 수 없다", maxCapacity)
                .isLessThanOrEqualTo(maxCapacity);

        // 실패 수는 전체 - 성공 수
        assertThat(failCount.get())
                .as("실패 수는 전체 요청에서 성공 수를 뺀 값이어야 한다")
                .isEqualTo(threadCount - successCount.get());

        // DB 잔여 인원 = 최대 인원 - 성공 수 (음수 불가)
        ReservationSlot updated = slotRepository.findBySlotIdAndDeletedAtIsNull(slot.getSlotId()).orElseThrow();
        assertThat(updated.getRemainingCapacity())
                .as("DB 잔여 인원은 음수가 될 수 없다")
                .isGreaterThanOrEqualTo(0);
        assertThat(updated.getRemainingCapacity())
                .as("DB 잔여 인원 = 최대 인원 - 성공 예약 수")
                .isEqualTo(maxCapacity - successCount.get());

        // Redis 잔여 인원 = DB 잔여 인원 (정합성)
        long redisRemaining = redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slot.getSlotId()).get();
        assertThat(redisRemaining)
                .as("Redis 잔여 인원이 DB 잔여 인원과 일치해야 한다")
                .isEqualTo(updated.getRemainingCapacity());
    }

    private CreateReservationRequest buildRequest(UUID slotId, int size, String bookerName, String bookerPhone) {
        CreateReservationRequest req = new CreateReservationRequest();
        ReflectionTestUtils.setField(req, "slotId", slotId);
        ReflectionTestUtils.setField(req, "bookerName", bookerName);
        ReflectionTestUtils.setField(req, "bookerPhone", bookerPhone);
        ReflectionTestUtils.setField(req, "reservationSize", size);
        return req;
    }
}
