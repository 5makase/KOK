package com.omakase.kok.store.domain.repository;

import com.omakase.kok.store.domain.entity.StoreOutboxEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreOutboxEventRepository {
    StoreOutboxEvent save(StoreOutboxEvent event);

    Optional<StoreOutboxEvent> findById(UUID outboxEventId);

    // 발행 대기 중인 이벤트 배치 조회 (대량 적체 시 OOM-메모리부족 방지)
    List<StoreOutboxEvent> findPendingEvents(int limit);

    // 발행 실패한 이벤트 배치 조회 (재시도 대상, 대량 적체 시 OOM-메모리부족 방지)
    List<StoreOutboxEvent> findFailedEvents(int limit);
}
