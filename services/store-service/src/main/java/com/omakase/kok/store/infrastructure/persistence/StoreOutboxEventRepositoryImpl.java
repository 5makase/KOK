package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.StoreOutboxEvent;
import com.omakase.kok.store.domain.enums.OutboxEventStatus;
import com.omakase.kok.store.domain.repository.StoreOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class StoreOutboxEventRepositoryImpl implements StoreOutboxEventRepository {

    private final StoreOutboxEventJpaRepository storeOutboxEventJpaRepository;

    @Override
    public StoreOutboxEvent save(StoreOutboxEvent event) {
        return storeOutboxEventJpaRepository.save(event);
    }

    @Override
    public Optional<StoreOutboxEvent> findById(UUID outboxEventId) {
        return storeOutboxEventJpaRepository.findById(outboxEventId);
    }

    @Override
    public List<StoreOutboxEvent> findPendingEvents(int limit) {
        return storeOutboxEventJpaRepository.findByStatusOrderByCreatedAtAsc(
            OutboxEventStatus.PENDING, PageRequest.of(0, limit)
        );
    }

    @Override
    public List<StoreOutboxEvent> findFailedEvents(int limit) {
        return storeOutboxEventJpaRepository.findByStatusOrderByCreatedAtAsc(
            OutboxEventStatus.FAILED, PageRequest.of(0, limit)
        );
    }
}
