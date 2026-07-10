package com.kok.review.domain.repository;

import com.kok.review.domain.entity.OutboxStatus;
import com.kok.review.domain.entity.ReviewOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
@Repository
public interface ReviewOutboxEventRepository extends JpaRepository<ReviewOutboxEvent, UUID> {
    // PENDING 이벤트를 오래된 순으로 조회 (한 번에 처리할 양 제한)
    List<ReviewOutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
