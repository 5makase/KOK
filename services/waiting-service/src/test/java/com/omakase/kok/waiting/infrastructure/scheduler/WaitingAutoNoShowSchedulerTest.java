package com.omakase.kok.waiting.infrastructure.scheduler;

import com.omakase.kok.waiting.application.service.WaitingService;
import com.omakase.kok.waiting.infrastructure.config.WaitingAutoNoShowProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WaitingAutoNoShowSchedulerTest {
    @Mock
    private WaitingService waitingService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @Test
    @DisplayName("자동 미입장 스케줄러는 분산락을 획득한 인스턴스에서만 처리한다")
    void autoNoShowExpiredWaitings_runsWhenLockAcquired() throws InterruptedException {
        WaitingAutoNoShowScheduler scheduler = scheduler();
        given(redissonClient.getLock("waiting:auto-no-show:lock")).willReturn(lock);
        given(lock.tryLock(0L, TimeUnit.MILLISECONDS)).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);

        scheduler.autoNoShowExpiredWaitings();

        then(waitingService).should().autoNoShowExpiredWaitings(100);
        then(lock).should().unlock();
    }

    @Test
    @DisplayName("자동 미입장 스케줄러는 분산락 획득 실패 시 처리하지 않는다")
    void autoNoShowExpiredWaitings_skipsWhenLockNotAcquired() throws InterruptedException {
        WaitingAutoNoShowScheduler scheduler = scheduler();
        given(redissonClient.getLock("waiting:auto-no-show:lock")).willReturn(lock);
        given(lock.tryLock(0L, TimeUnit.MILLISECONDS)).willReturn(false);

        scheduler.autoNoShowExpiredWaitings();

        then(waitingService).should(never()).autoNoShowExpiredWaitings(100);
        then(lock).should(never()).unlock();
    }

    private WaitingAutoNoShowScheduler scheduler() {
        return new WaitingAutoNoShowScheduler(
                waitingService,
                redissonClient,
                new WaitingAutoNoShowProperties("0 * * * * *", 100)
        );
    }
}
