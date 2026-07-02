package com.omakase.kok.reservation.application.service;

import com.omakase.kok.reservation.application.dto.CreateReservationRequest;
import com.omakase.kok.reservation.application.dto.SlotCapacityRestoreResponse;
import com.omakase.kok.reservation.domain.entity.Reservation;
import com.omakase.kok.reservation.domain.entity.ReservationSlot;
import com.omakase.kok.reservation.domain.repository.ReservationOutboxEventRepository;
import com.omakase.kok.reservation.domain.repository.ReservationRepository;
import com.omakase.kok.reservation.domain.repository.ReservationSlotRepository;
import com.omakase.kok.reservation.infrastructure.client.PaymentFeignClient;
import com.omakase.kok.reservation.infrastructure.client.StoreServiceFeignClient;
import com.omakase.kok.reservation.infrastructure.scheduler.CapacityDriftDetectionScheduler;
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

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
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
@DisplayName("슬롯 정원 Redis 복구 통합 테스트")
class ReservationCapacityRestoreIntegrationTest {

    private static final String SLOT_CAPACITY_KEY = "slot:capacity:";

    @Autowired private ReservationService reservationService;
    @Autowired private ReservationSlotRepository slotRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationOutboxEventRepository outboxEventRepository;
    @Autowired private CapacityDriftDetectionScheduler driftDetectionScheduler;
    @Autowired private RedissonClient redissonClient;

    @MockBean private PaymentFeignClient paymentFeignClient;
    @MockBean private StoreServiceFeignClient storeServiceFeignClient;

    private ReservationSlot slot;

    @BeforeEach
    void setUp() {
        slot = ReservationSlot.builder()
                .storeId(UUID.randomUUID())
                .storeName("테스트 매장")
                .slotDate(LocalDate.now().plusDays(5))
                .slotTime(LocalTime.of(18, 0))
                .maxCapacity(10)
                .depositRequired(false)
                .depositAmount(null)
                .build();
        slotRepository.save(slot);
        redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slot.getSlotId()).set(10);
    }

    @AfterEach
    void tearDown() {
        outboxEventRepository.deleteAll();
        reservationRepository.deleteAll();
        slotRepository.deleteAll();
        redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slot.getSlotId()).delete();
    }

    @Test
    @DisplayName("Redis 값이 오염되어 있어도 복구 호출 시 DB(CONFIRMED)-PAYMENT_PENDING 기준으로 재계산된다")
    void restoreCapacity_recalculatesFromDbAndPendingReservations() {
        reservationRepository.save(buildConfirmedReservation(3));
        slot.decreaseCapacity(3);
        slotRepository.save(slot);
        reservationRepository.save(buildPendingReservation(2));

        // Redis 장애로 값이 유실/오염된 상황을 흉내
        redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slot.getSlotId()).set(999);

        SlotCapacityRestoreResponse response = reservationService.restoreCapacity(slot.getStoreId(), slot.getSlotDate());

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getAfter()).isEqualTo(5L); // dbRemaining(7) - pending(2)
        assertThat(redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slot.getSlotId()).get()).isEqualTo(5L);
    }

    @Test
    @DisplayName("정원 복구가 동시 예약 요청과 겹쳐도 초과예약 없이 Redis/DB가 최종적으로 일치한다")
    void restoreCapacity_concurrentWithReservations_keepsCapacityConsistent() throws InterruptedException {
        int reservationThreadCount = 8; // maxCapacity(10)보다 총 요청 인원이 많도록(2명 x 8 = 16명) 경합 유도
        int restoreThreadCount = 3;
        int totalThreadCount = reservationThreadCount + restoreThreadCount;

        ExecutorService executor = Executors.newFixedThreadPool(totalThreadCount);
        CountDownLatch ready = new CountDownLatch(totalThreadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(totalThreadCount);
        AtomicInteger reservationSuccessCount = new AtomicInteger(0);

        for (int i = 0; i < reservationThreadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    CreateReservationRequest request = buildRequest(
                            slot.getSlotId(), 2, "예약자" + idx, "010-0000-00" + String.format("%02d", idx));
                    ready.countDown();
                    start.await();
                    reservationService.createReservation(request, UUID.randomUUID());
                    reservationSuccessCount.incrementAndGet();
                } catch (Exception ignored) {
                    // 정원 초과/락 타임아웃으로 인한 실패는 정상적인 결과이므로 무시
                } finally {
                    done.countDown();
                }
            });
        }

        for (int i = 0; i < restoreThreadCount; i++) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    reservationService.restoreCapacity(slot.getStoreId(), slot.getSlotDate());
                } catch (Exception ignored) {
                    // 락 타임아웃은 스킵 처리되므로 예외가 나지 않지만, 방어적으로 무시
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(10, TimeUnit.SECONDS))
                .as("모든 스레드가 제한 시간 내에 준비를 마쳐야 한다")
                .isTrue();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS))
                .as("모든 스레드가 제한 시간 내에 완료되어야 한다 (데드락 시 실패)")
                .isTrue();
        executor.shutdown();

        assertThat(reservationSuccessCount.get())
                .as("최소 1건 이상의 예약이 성공해야 한다 (경쟁이 실제로 있었음을 보장)")
                .isGreaterThan(0);

        // 마지막에 한 번 더 복구를 호출해 DB 기준으로 정합성을 재확인한다
        reservationService.restoreCapacity(slot.getStoreId(), slot.getSlotDate());

        ReservationSlot updated = slotRepository.findBySlotIdAndDeletedAtIsNull(slot.getSlotId()).orElseThrow();
        long redisRemaining = redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slot.getSlotId()).get();

        assertThat(updated.getRemainingCapacity())
                .as("DB 잔여 인원은 음수가 될 수 없다")
                .isGreaterThanOrEqualTo(0);
        assertThat(updated.getMaxCapacity() - updated.getRemainingCapacity())
                .as("확정된 예약 인원(사용량)은 성공한 예약 인원 합계와 일치해야 한다")
                .isEqualTo(reservationSuccessCount.get() * 2);
        assertThat(redisRemaining)
                .as("Redis 잔여 인원이 DB 잔여 인원과 일치해야 한다")
                .isEqualTo(updated.getRemainingCapacity());
    }

    @Test
    @DisplayName("드리프트 감지 스케줄러는 불일치를 감지해도 Redis/DB 값을 변경하지 않는다")
    void driftDetectionScheduler_doesNotMutateValues() {
        redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slot.getSlotId()).set(999);
        int remainingBefore = slot.getRemainingCapacity();

        driftDetectionScheduler.detectCapacityDrift();

        assertThat(redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slot.getSlotId()).get())
                .as("자동 수정은 하지 않으므로 Redis 값은 그대로여야 한다")
                .isEqualTo(999L);
        ReservationSlot unchanged = slotRepository.findBySlotIdAndDeletedAtIsNull(slot.getSlotId()).orElseThrow();
        assertThat(unchanged.getRemainingCapacity())
                .as("자동 수정은 하지 않으므로 DB 값도 그대로여야 한다")
                .isEqualTo(remainingBefore);
    }

    private Reservation buildConfirmedReservation(int size) {
        Reservation reservation = Reservation.builder()
                .slotId(slot.getSlotId())
                .userId(UUID.randomUUID())
                .storeId(slot.getStoreId())
                .storeName(slot.getStoreName())
                .scheduledAt(LocalDateTime.of(slot.getSlotDate(), slot.getSlotTime()))
                .bookerName("확정예약자")
                .bookerPhone("010-0000-1000")
                .reservationSize(size)
                .build();
        reservation.confirm();
        return reservation;
    }

    private Reservation buildPendingReservation(int size) {
        return Reservation.builder()
                .slotId(slot.getSlotId())
                .userId(UUID.randomUUID())
                .storeId(slot.getStoreId())
                .storeName(slot.getStoreName())
                .scheduledAt(LocalDateTime.of(slot.getSlotDate(), slot.getSlotTime()))
                .bookerName("대기예약자")
                .bookerPhone("010-0000-2000")
                .reservationSize(size)
                .build();
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
