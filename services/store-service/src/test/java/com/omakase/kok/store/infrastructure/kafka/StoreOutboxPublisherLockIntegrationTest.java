package com.omakase.kok.store.infrastructure.kafka;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * StoreOutboxPublisher 분산 락(Redisson RLock) 통합 테스트 -> Spring 컨텍스트 없이 RedissonClient만 직접 생성해서 검증
 * 실행 전제: docker compose up -d redis (localhost:6379)
 */
class StoreOutboxPublisherLockIntegrationTest {

    private static final String LOCK_KEY = "outbox:store-publisher:lock:test";
    private static RedissonClient redissonClient;

    @BeforeAll
    static void setUpRedisson() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://localhost:6379");
        redissonClient = Redisson.create(config);
    }

    @AfterAll
    static void shutdownRedisson() {
        redissonClient.shutdown();
    }

    @AfterEach
    void cleanUp() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        if (lock.isLocked()) {
            lock.forceUnlock();
        }
    }

    @Test
    @DisplayName("동시에 여러 스레드가 같은 락을 시도하면 하나만 획득에 성공한다")
    void only_one_thread_acquires_lock_when_competing_simultaneously() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                RLock lock = redissonClient.getLock(LOCK_KEY);
                readyLatch.countDown();
                try {
                    startLatch.await();
                    if (lock.tryLock(0, 5, TimeUnit.SECONDS)) {
                        try {
                            successCount.incrementAndGet();
                            Thread.sleep(500);
                        } finally {
                            lock.unlock();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("락 해제 후에는 다른 스레드가 순차적으로 락을 획득할 수 있다")
    void lock_can_be_reacquired_after_release() throws InterruptedException {
        RLock firstLock = redissonClient.getLock(LOCK_KEY);
        boolean firstAcquired = firstLock.tryLock(0, 5, TimeUnit.SECONDS);
        assertThat(firstAcquired).isTrue();
        firstLock.unlock();

        RLock secondLock = redissonClient.getLock(LOCK_KEY);
        boolean secondAcquired = secondLock.tryLock(0, 5, TimeUnit.SECONDS);

        assertThat(secondAcquired).isTrue();
        secondLock.unlock();
    }
}