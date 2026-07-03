package com.omakase.kok.reservation.infrastructure.scheduler;

import com.omakase.kok.reservation.domain.entity.ReservationSlot;
import com.omakase.kok.reservation.domain.enums.ReservationStatus;
import com.omakase.kok.reservation.domain.enums.SlotStatus;
import com.omakase.kok.reservation.domain.repository.ReservationRepository;
import com.omakase.kok.reservation.domain.repository.ReservationRepository.SlotPendingSize;
import com.omakase.kok.reservation.domain.repository.ReservationSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis(slot:capacity:{slotId})와 DB(remainingCapacity - PAYMENT_PENDING) 값의 불일치를 감지해 로그만 남긴다.
 * 자동 수정은 하지 않는다 — 실제 복구는 항상 ReservationService.restoreCapacity(관리자 전용 API)를 통해서만 수행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CapacityDriftDetectionScheduler {

    private static final String SCHEDULER_LOCK_KEY = "reservation:scheduler:capacity-drift-check";
    private static final String SLOT_CAPACITY_KEY = "slot:capacity:";
    private static final int LOG_SAMPLE_LIMIT = 20;

    private final ReservationSlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final RedissonClient redissonClient;

    @Scheduled(fixedRate = 300000)
    public void detectCapacityDrift() {
        RLock lock = redissonClient.getLock(SCHEDULER_LOCK_KEY);
        try {
            if (!lock.tryLock(0, 30, TimeUnit.SECONDS)) {
                return;
            }

            List<ReservationSlot> slots = slotRepository
                    .findByStatusAndSlotDateGreaterThanEqualAndDeletedAtIsNull(SlotStatus.OPEN, LocalDate.now());
            if (slots.isEmpty()) {
                return;
            }

            List<UUID> slotIds = slots.stream().map(ReservationSlot::getSlotId).toList();
            Map<UUID, Long> pendingSizeBySlot = reservationRepository
                    .sumPendingSizeBySlotIds(slotIds, ReservationStatus.PAYMENT_PENDING).stream()
                    .collect(Collectors.toMap(SlotPendingSize::getSlotId, SlotPendingSize::getPendingSize));

            int driftedCount = 0;
            for (ReservationSlot slot : slots) {
                long pending = pendingSizeBySlot.getOrDefault(slot.getSlotId(), 0L);
                long expected = slot.effectiveRemainingCapacity(pending);
                long actual = redissonClient.getAtomicLong(SLOT_CAPACITY_KEY + slot.getSlotId()).get();

                if (actual != expected) {
                    driftedCount++;
                    if (driftedCount <= LOG_SAMPLE_LIMIT) {
                        log.error("Redis-DB 정원 불일치 감지 - slotId: {}, storeId: {}, redis: {}, expected: {} " +
                                        "(dbRemaining: {}, pending: {})",
                                slot.getSlotId(), slot.getStoreId(), actual, expected,
                                slot.getRemainingCapacity(), pending);
                    }
                }
            }

            if (driftedCount > 0) {
                log.error("정원 드리프트 감지 스캔 완료 - 전체: {}건, 불일치: {}건 (로그 상세 표시: 최대 {}건). " +
                                "관리자가 /api/v1/internal/reservations/stores/{{storeId}}/capacity/restore 호출 필요",
                        slots.size(), driftedCount, LOG_SAMPLE_LIMIT);
            } else {
                log.debug("정원 드리프트 감지 스캔 완료 - 전체: {}건, 불일치 없음", slots.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("정원 드리프트 감지 스케줄러 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
