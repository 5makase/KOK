package com.omakase.kok.notification.integration;

import com.omakase.kok.notification.application.service.SlackSendService;
import com.omakase.kok.notification.domain.entity.Notification;
import com.omakase.kok.notification.domain.entity.SlackSendLog;
import com.omakase.kok.notification.domain.enums.NotificationType;
import com.omakase.kok.notification.domain.enums.ReferenceType;
import com.omakase.kok.notification.domain.repository.NotificationRepository;
import com.omakase.kok.notification.domain.repository.SlackSendLogRepository;
import com.omakase.kok.notification.infrastructure.scheduler.RetryScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@DisplayName("RetryScheduler ShedLock 통합 테스트")
class RetrySchedulerIntegrationTest extends AbstractIntegrationTest {

    @Autowired RetryScheduler retryScheduler;
    @Autowired NotificationRepository notificationRepository;
    @Autowired SlackSendLogRepository slackSendLogRepository;

    // SlackSendService 전체를 mock — 실제 Slack 발송 차단 및 호출 횟수 검증
    @MockitoBean SlackSendService slackSendService;

    @BeforeEach
    void setUp() {
        slackSendLogRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @Test
    @DisplayName("두 인스턴스 동시 실행 시 ShedLock으로 send() 1회만 호출")
    void shedLock_Multi_Instance() throws InterruptedException {
        // given — FAILED 상태 SlackSendLog + 연결된 Notification 적재
        Notification notification = notificationRepository.save(
                Notification.create(
                        UUID.randomUUID(), UUID.randomUUID(),
                        ReferenceType.WAITING, NotificationType.WAITING_REGISTERED,
                        Map.of("storeName", "테스트 식당", "waitingNumber", 1, "peopleCount", 2)
                )
        );
        SlackSendLog failedLog = slackSendLogRepository.save(buildFailedLog(notification.getNotificationId()));

        AtomicInteger sendCallCount = new AtomicInteger(0);

        // send() 호출 시 카운트 증가 + 200ms 점유 (Thread 2가 lock 획득 시도하는 동안 Thread 1이 lock 유지)
        doAnswer(inv -> {
            sendCallCount.incrementAndGet();
            Thread.sleep(200);
            return null;
        }).when(slackSendService).send(any(UUID.class), anyString(), any(SlackSendLog.class));

        // CyclicBarrier: 두 스레드가 모두 준비된 시점에 동시 출발 보장
        CyclicBarrier barrier = new CyclicBarrier(2);
        CountDownLatch doneLatch = new CountDownLatch(2);

        for (int i = 0; i < 2; i++) {
            new Thread(() -> {
                try {
                    barrier.await(); // 두 스레드 모두 도착할 때까지 대기 후 동시 출발
                    retryScheduler.retryFailedSlackSend(); // Spring AOP 프록시 통해 ShedLock 적용
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (BrokenBarrierException e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertThat(completed).as("두 스레드가 10초 내에 완료되지 않음").isTrue();

        // then — ShedLock으로 한 스레드만 실행됨
        assertThat(sendCallCount.get()).isEqualTo(1);
    }

    // ── helper ───────────────────────────────────────────

    private SlackSendLog buildFailedLog(UUID notificationId) {
        SlackSendLog log = SlackSendLog.create(notificationId);
        log.markAsFailed("테스트 강제 실패");
        return log;
    }
}
