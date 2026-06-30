package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.StoreOutboxEvent;
import com.omakase.kok.store.domain.enums.OutboxEventStatus;
import com.omakase.kok.store.domain.repository.StoreOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class StoreOutboxEventRepositoryImpl implements StoreOutboxEventRepository {

    private final StoreOutboxEventJpaRepository storeOutboxEventJpaRepository;

    @Override
    public StoreOutboxEvent save(StoreOutboxEvent event) {
        return storeOutboxEventJpaRepository.save(event);
    }

    @Override
    public List<StoreOutboxEvent> findPendingEvents() {
        return storeOutboxEventJpaRepository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);
    }

    @Override
    public List<StoreOutboxEvent> findPendingEventsBatch(int limit) {
        return storeOutboxEventJpaRepository.findByStatusOrderByCreatedAtAsc(
            OutboxEventStatus.PENDING, PageRequest.of(0, limit)
        );
    }
}
