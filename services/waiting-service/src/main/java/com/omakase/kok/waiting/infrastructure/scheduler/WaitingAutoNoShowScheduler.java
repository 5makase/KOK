package com.omakase.kok.waiting.infrastructure.scheduler;

import com.omakase.kok.waiting.application.service.WaitingService;
import com.omakase.kok.waiting.infrastructure.config.WaitingAutoNoShowProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingAutoNoShowScheduler {
    private static final String AUTO_NO_SHOW_LOCK_KEY = "waiting:auto-no-show:lock";
    private static final long AUTO_NO_SHOW_LOCK_WAIT_MS = 0L;

    private final WaitingService waitingService;
    private final RedissonClient redissonClient;
    private final WaitingAutoNoShowProperties waitingAutoNoShowProperties;

    // 호출 제한 시간이 지난 CALLED 상태 웨이팅을 자동 미입장 처리
    @Scheduled(cron = "${waiting.auto-no-show.cron}")
    public void autoNoShowExpiredWaitings() {
        RLock lock = redissonClient.getLock(AUTO_NO_SHOW_LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(
                    AUTO_NO_SHOW_LOCK_WAIT_MS,
                    TimeUnit.MILLISECONDS
            );
            if (!locked) {
                return;
            }

            int processedCount = waitingService.autoNoShowExpiredWaitings(waitingAutoNoShowProperties.batchSize());
            if (processedCount > 0) {
                log.info("Processed expired waiting auto no-show. count={}", processedCount);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while acquiring waiting auto no-show lock.", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
