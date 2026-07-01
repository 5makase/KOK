package com.omakase.kok.reservation.application.service;

import com.omakase.kok.reservation.application.dto.CreateReservationRequest;
import com.omakase.kok.reservation.application.dto.UpdateSlotRequest;
import com.omakase.kok.reservation.domain.entity.ReservationSlot;
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
@DisplayName("슬롯 수정 동시성 통합 테스트")
class SlotUpdateConcurrencyIntegrationTest {

    private static final String SLOT_CAPACITY_KEY = "slot:capacity:";

    @Autowired private SlotService slotService;
    @Autowired private ReservationService reservationService;
    @Autowired private ReservationSlotRepository slotRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationOutboxEventRepository outboxEventRepository;
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
    @DisplayName("슬롯 정원 축소와 동시 예약 요청이 뒤섞여도 DB/Redis 잔여 인원이 일관되게 유지된다")
    void concurrentUpdateAndReservation_keepsCapacityConsistent() throws InterruptedException {
        int[] targetCapacities = {8, 6, 4};
        int updateThreadCount = targetCapacities.length;
        int reservationThreadCount = 5;
        int totalThreadCount = updateThreadCount + reservationThreadCount;

        ExecutorService executor = Executors.newFixedThreadPool(totalThreadCount);
        CountDownLatch ready = new CountDownLatch(totalThreadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(totalThreadCount);

        AtomicInteger updateSuccessCount = new AtomicInteger(0);
        AtomicInteger reservationSuccessCount = new AtomicInteger(0);

        for (int i = 0; i < updateThreadCount; i++) {
            final int newCapacity = targetCapacities[i];
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    UpdateSlotRequest req = new UpdateSlotRequest();
                    ReflectionTestUtils.setField(req, "maxCapacity", newCapacity);
                    slotService.updateSlot(slot.getSlotId(), req, slot.getStoreId());
                    updateSuccessCount.incrementAndGet();
                } catch (Exception ignored) {
                    // 락 경쟁/정원 검증 실패는 정상적인 결과이므로 무시
                } finally {
                    done.countDown();
                }
            });
        }

        for (int i = 0; i < reservationThreadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    CreateReservationRequest request = buildRequest(
                            slot.getSlotId(), 1, "예약자" + idx, "010-0000-00" + String.format("%02d", idx));
                    ready.countDown();
                    start.await();
                    reservationService.createReservation(request, UUID.randomUUID());
                    reservationSuccessCount.incrementAndGet();
                } catch (Exception ignored) {
                    // 정원 초과로 인한 실패는 정상적인 결과이므로 무시
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

        // 대부분의 스레드가 락 타임아웃으로 실패하면 실제 동시성 경쟁 없이도
        // 아래 정합성 검증이 트리비얼하게 통과할 수 있으므로, 실제로 락 경쟁이 있었음을 보장한다.
        assertThat(updateSuccessCount.get())
                .as("최소 1건 이상의 슬롯 수정이 성공해야 한다")
                .isGreaterThan(0);
        assertThat(reservationSuccessCount.get())
                .as("최소 1건 이상의 예약이 성공해야 한다")
                .isGreaterThan(0);

        ReservationSlot updated = slotRepository.findBySlotIdAndDeletedAtIsNull(slot.getSlotId()).orElseThrow();
        long redisRemaining = redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slot.getSlotId()).get();

        assertThat(updated.getRemainingCapacity())
                .as("DB 잔여 인원은 음수가 될 수 없다")
                .isGreaterThanOrEqualTo(0);
        assertThat(updated.getMaxCapacity() - updated.getRemainingCapacity())
                .as("확정된 예약 인원(사용량)은 성공한 예약 수와 일치해야 한다 — 락이 없다면 update의 lost write로 어긋날 수 있다")
                .isEqualTo(reservationSuccessCount.get());
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
