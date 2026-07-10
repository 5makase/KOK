package com.omakase.kok.waiting.domain.repository;

import com.omakase.kok.waiting.domain.entity.WaitingOutboxEvent;
import com.omakase.kok.waiting.domain.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WaitingOutboxEventRepository extends JpaRepository<WaitingOutboxEvent, UUID> {
    // 상태별 Outbox 이벤트 조회
    List<WaitingOutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

    // 상태별 Outbox 이벤트 배치 조회
    List<WaitingOutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
}
