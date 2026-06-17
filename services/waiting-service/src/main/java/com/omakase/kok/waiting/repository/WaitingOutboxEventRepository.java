package com.omakase.kok.waiting.repository;

import com.omakase.kok.waiting.domain.entity.WaitingOutboxEvent;
import com.omakase.kok.waiting.domain.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WaitingOutboxEventRepository extends JpaRepository<WaitingOutboxEvent, UUID> {
    // 상태별 전체 조회
    List<WaitingOutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
