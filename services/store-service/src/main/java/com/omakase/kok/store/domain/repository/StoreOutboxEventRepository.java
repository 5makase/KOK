package com.omakase.kok.store.domain.repository;

import com.omakase.kok.store.domain.entity.StoreOutboxEvent;

import java.util.List;

public interface StoreOutboxEventRepository {
    StoreOutboxEvent save(StoreOutboxEvent event);

    // 발행 대기 중인 전체 이벤트 조회
    List<StoreOutboxEvent> findPendingEvents();

    // 발행 대기 중인 이벤트 배치 조회 (대량 적체 시 OOM-메모리부족 방지)
    List<StoreOutboxEvent> findPendingEventsBatch(int limit);
}
