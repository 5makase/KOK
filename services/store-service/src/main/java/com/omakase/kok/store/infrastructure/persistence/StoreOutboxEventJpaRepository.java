package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.StoreOutboxEvent;
import com.omakase.kok.store.domain.enums.OutboxEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StoreOutboxEventJpaRepository extends JpaRepository<StoreOutboxEvent, UUID> {
    List<StoreOutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status);

    List<StoreOutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status, Pageable pageable);
}
