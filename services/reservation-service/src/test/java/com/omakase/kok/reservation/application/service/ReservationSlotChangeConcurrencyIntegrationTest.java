package com.omakase.kok.reservation.application.service;

import com.omakase.kok.reservation.application.dto.ChangeReservationRequest;
import com.omakase.kok.reservation.domain.entity.Reservation;
import com.omakase.kok.reservation.domain.entity.ReservationSlot;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import com.omakase.kok.reservation.domain.repository.ReservationOutboxEventRepository;
import com.omakase.kok.reservation.domain.repository.ReservationRepository;
import com.omakase.kok.reservation.domain.repository.ReservationSlotRepository;
import com.omakase.kok.reservation.infrastructure.client.PaymentFeignClient;
import com.omakase.kok.reservation.infrastructure.client.StoreServiceFeignClient;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"reservation.events.v1"})
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@DisplayName("예약 슬롯 변경 동시성 통합 테스트")
class ReservationSlotChangeConcurrencyIntegrationTest {

    private static final String SLOT_CAPACITY_KEY = "slot:capacity:";
    private static final int SLOT_MAX_CAPACITY = 10;
    private static final int RESERVATIONS_PER_SLOT = 5;

    @Autowired private ReservationService reservationService;
    @Autowired private ReservationSlotRepository slotRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationOutboxEventRepository outboxEventRepository;
    @Autowired private RedissonClient redissonClient;

    @MockBean private PaymentFeignClient paymentFeignClient;
    @MockBean private StoreServiceFeignClient storeServiceFeignClient;

    private UUID storeId;
    private ReservationSlot slotA;
    private ReservationSlot slotB;

    @BeforeEach
    void setUp() {
        storeId = UUID.randomUUID();
        slotA = buildSlot(storeId, LocalDate.now().plusDays(5), LocalTime.of(18, 0));
        slotB = buildSlot(storeId, LocalDate.now().plusDays(6), LocalTime.of(19, 0));
        slotRepository.save(slotA);
        slotRepository.save(slotB);
    }

    @AfterEach
    void tearDown() {
        outboxEventRepository.deleteAll();
        reservationRepository.deleteAll();
        slotRepository.deleteAll();
        redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slotA.getSlotId()).delete();
        redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slotB.getSlotId()).delete();
    }

    @Test
    @DisplayName("양방향 동시 슬롯 변경 요청이 데드락 없이 처리되고 DB/Redis 잔여 인원이 일관되게 유지된다")
    void concurrentBidirectionalSlotChange_keepsCapacityConsistentWithoutDeadlock() throws InterruptedException {
        List<Reservation> inSlotA = new ArrayList<>();
        List<Reservation> inSlotB = new ArrayList<>();
        for (int i = 0; i < RESERVATIONS_PER_SLOT; i++) {
            inSlotA.add(saveConfirmedReservation(slotA, storeId));
            inSlotB.add(saveConfirmedReservation(slotB, storeId));
        }

        // 정원 10 중 5명이 이미 사용 중 → 슬롯당 잔여 5
        slotA.decreaseCapacity(RESERVATIONS_PER_SLOT);
        slotB.decreaseCapacity(RESERVATIONS_PER_SLOT);
        slotRepository.save(slotA);
        slotRepository.save(slotB);
        redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slotA.getSlotId()).set(SLOT_MAX_CAPACITY - RESERVATIONS_PER_SLOT);
        redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slotB.getSlotId()).set(SLOT_MAX_CAPACITY - RESERVATIONS_PER_SLOT);

        int totalThreadCount = RESERVATIONS_PER_SLOT * 2;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreadCount);
        CountDownLatch ready = new CountDownLatch(totalThreadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(totalThreadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (Reservation r : inSlotA) {
            submitSlotChange(executor, ready, start, done, successCount, r.getReservationId(), r.getUserId(), slotB.getSlotId());
        }
        for (Reservation r : inSlotB) {
            submitSlotChange(executor, ready, start, done, successCount, r.getReservationId(), r.getUserId(), slotA.getSlotId());
        }

        assertThat(ready.await(10, TimeUnit.SECONDS))
                .as("모든 스레드가 제한 시간 내에 준비를 마쳐야 한다")
                .isTrue();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS))
                .as("모든 스레드가 제한 시간 내에 완료되어야 한다 (데드락 시 실패)")
                .isTrue();
        executor.shutdown();

        assertThat(successCount.get())
                .as("최소 1건 이상의 슬롯 변경이 성공해야 한다")
                .isGreaterThan(0);

        ReservationSlot updatedA = slotRepository.findBySlotIdAndDeletedAtIsNull(slotA.getSlotId()).orElseThrow();
        ReservationSlot updatedB = slotRepository.findBySlotIdAndDeletedAtIsNull(slotB.getSlotId()).orElseThrow();
        long redisA = redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slotA.getSlotId()).get();
        long redisB = redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slotB.getSlotId()).get();

        assertThat(updatedA.getRemainingCapacity()).isGreaterThanOrEqualTo(0);
        assertThat(updatedB.getRemainingCapacity()).isGreaterThanOrEqualTo(0);
        assertThat(redisA)
                .as("슬롯 A의 Redis 잔여 인원이 DB와 일치해야 한다")
                .isEqualTo(updatedA.getRemainingCapacity());
        assertThat(redisB)
                .as("슬롯 B의 Redis 잔여 인원이 DB와 일치해야 한다")
                .isEqualTo(updatedB.getRemainingCapacity());
        // 양방향 스왑은 두 슬롯 사이에서 예약을 재배치할 뿐 총 점유 인원을 바꾸지 않으므로,
        // 성공/실패 조합과 무관하게 두 슬롯의 잔여 인원 합은 항상 보존되어야 한다.
        assertThat(updatedA.getRemainingCapacity() + updatedB.getRemainingCapacity())
                .as("두 슬롯의 총 잔여 인원 합은 변하지 않아야 한다")
                .isEqualTo((SLOT_MAX_CAPACITY - RESERVATIONS_PER_SLOT) * 2);
    }

    private void submitSlotChange(ExecutorService executor, CountDownLatch ready, CountDownLatch start, CountDownLatch done,
                                   AtomicInteger successCount, UUID reservationId, UUID userId, UUID targetSlotId) {
        executor.submit(() -> {
            try {
                ChangeReservationRequest request = new ChangeReservationRequest();
                ReflectionTestUtils.setField(request, "newSlotId", targetSlotId);
                ready.countDown();
                start.await();
                reservationService.changeReservation(reservationId, userId, request);
                successCount.incrementAndGet();
            } catch (Exception ignored) {
                // 락 경쟁/정원 초과로 인한 실패는 정상적인 결과이므로 무시
            } finally {
                done.countDown();
            }
        });
    }

    private ReservationSlot buildSlot(UUID storeId, LocalDate date, LocalTime time) {
        return ReservationSlot.builder()
                .storeId(storeId)
                .storeName("테스트 매장")
                .slotDate(date)
                .slotTime(time)
                .maxCapacity(SLOT_MAX_CAPACITY)
                .depositRequired(false)
                .depositAmount(null)
                .build();
    }

    private Reservation saveConfirmedReservation(ReservationSlot slot, UUID storeId) {
        Reservation r = Reservation.builder()
                .slotId(slot.getSlotId())
                .userId(UUID.randomUUID())
                .storeId(storeId)
                .storeName(slot.getStoreName())
                .scheduledAt(LocalDateTime.of(slot.getSlotDate(), slot.getSlotTime()))
                .bookerName("테스터")
                .bookerPhone("010-0000-0000")
                .reservationSize(1)
                .build();
        ReflectionTestUtils.setField(r, "status", ReservationStatus.CONFIRMED);
        reservationRepository.save(r);
        return r;
    }
}
