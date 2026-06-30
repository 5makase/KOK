package com.omakase.kok.store.infrastructure.kafka;

import com.omakase.kok.store.domain.entity.StoreOutboxEvent;
import com.omakase.kok.store.domain.enums.OutboxEventStatus;
import com.omakase.kok.store.domain.repository.StoreOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoreOutboxPublisher {

    private static final String TOPIC = "store.events.v1";

    // 다중 인스턴스 환경에서 스케줄러 동시 실행으로 인한 중복 발행 방지
    // 동시에 같은 Outbox 테이블을 건드리지 않도록 PENDING/FAILED 두 스케줄러가 같은 키를 공유
    private static final String LOCK_KEY = "outbox:store-publisher:lock";

    // 한 번에 처리할 최대 이벤트 수 (대량 적체 시 OOM 방지) -> 장애 복구 후 한꺼번에 쌓인 PENDING을 안전하게 나눠 처리하기 위한 상한선 설정
    private static final int BATCH_SIZE = 100;

    private static final int KAFKA_TIMEOUT_SECONDS = 5;

    private final StoreOutboxEventRepository storeOutboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RedissonClient redissonClient;
    private final PlatformTransactionManager transactionManager;

    // PENDING 이벤트 폴링 → 발행 시도 (5초 주기)
    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            // 락 획득 실패 시(다른 인스턴스가 처리 중) 즉시 종료: 대기하지 않음
            if (!lock.tryLock(0, 5, TimeUnit.SECONDS)) return;

            List<StoreOutboxEvent> events = storeOutboxEventRepository.findPendingEvents(BATCH_SIZE);
            for (StoreOutboxEvent event : events) {
                processSingleEvent(event.getStoreOutboxEventId());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Outbox Publisher 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    // FAILED 이벤트 재시도
    @Scheduled(fixedDelay = 60000)
    public void retryFailedEvents() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            if (!lock.tryLock(0, 5, TimeUnit.SECONDS)) return;

            List<StoreOutboxEvent> events = storeOutboxEventRepository.findFailedEvents(BATCH_SIZE);
            for (StoreOutboxEvent event : events) {
                retrySingleEvent(event.getStoreOutboxEventId());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Outbox Publisher 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    // 이벤트별 독립 트랜잭션: 조회 시점과 처리 시점 사이 상태가 바뀌었을 수 있어 PENDING 여부 재확인
    private void processSingleEvent(UUID outboxEventId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.execute(status -> {
            StoreOutboxEvent event = storeOutboxEventRepository.findById(outboxEventId).orElse(null);
            if (event == null || event.getStatus() != OutboxEventStatus.PENDING) {
                return null;
            }

            publish(event);
            return null;
        });
    }

    // 이벤트별 독립 트랜잭션. FAILED 상태 재확인 후 PENDING으로 되돌리고 재발행 시도
    private void retrySingleEvent(UUID outboxEventId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.execute(status -> {
            StoreOutboxEvent event = storeOutboxEventRepository.findById(outboxEventId).orElse(null);
            if (event == null || event.getStatus() != OutboxEventStatus.FAILED) {
                return null;
            }

            try {
                event.retry();
                publish(event);
            } catch (IllegalStateException e) {
                log.warn("재시도 불가능한 상태 - eventId: {}, status: {}", event.getStoreOutboxEventId(), event.getStatus(), e);
            }
            return null;
        });
    }

    // 단건 발행 시도: 성공 시 PUBLISHED, 실패 시 FAILED 상태 전이(Partition Key로 storeId 사용 -> 동일 매장 이벤트 순서 보장)
    private void publish(StoreOutboxEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event.getStoreId().toString(), event.getPayload())
                .get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            event.published();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Kafka 발행 중 인터럽트 발생 - eventId: {}", event.getStoreOutboxEventId(), e);
            event.failed("Kafka publish interrupted");
        } catch (Exception e) {
            log.error("Kafka 발행 실패 - eventId: {}, storeId: {}", event.getStoreOutboxEventId(), event.getStoreId(), e);
            event.failed(e.getMessage());
        }
    }

}

