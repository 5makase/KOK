package com.kok.review.infrastructure.messaging;

import com.kok.review.domain.entity.OutboxStatus;
import com.kok.review.domain.entity.ReviewOutboxEvent;
import com.kok.review.domain.repository.ReviewOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewOutboxPublisher {

    private static final String TOPIC = "review.events.v1";

    private final ReviewOutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 5초마다 PENDING 이벤트를 Kafka로 발행
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<ReviewOutboxEvent> pendingEvents =
                outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        if (pendingEvents.isEmpty()) {
            return;
        }

        for (ReviewOutboxEvent event : pendingEvents) {
            try {
                //send()는 비동기 -> get()으로 브로커를 기다림
                kafkaTemplate.send(TOPIC, event.getStoreId().toString(), event.getPayload()).get(5, TimeUnit.SECONDS);
                                                                                            /*get() :브로커가 메세지를 받았는지 확인하는 메서드.
                                                                                            - 5초 안으로 브로커가 메세지를 받았는지 확인이 된다면, markPublished를 실행
                                                                                            - 5초 안으로 못 받았으면 예외를 발생시킴.*/
                //확인된 후에만 PUBLISHED
                event.markPublished();
                log.info("리뷰 이벤트 발행 완료. type={}, reviewId={}",
                        event.getEventType(), event.getReviewId());
            } catch (InterruptedException e) { /*Interrupted 예외가 발생되는 경우 : 발행 대기(get) 중에 스레드 종료 신호가 온 경우.
                                                - (예: 서버 종료/재배포 시 스케줄러 스레드를 정리하려고 인터럽트를 보냄)
                                                - 발행 실패가 아니라 정상적인 종료 절차이므로, markFailed가 아니라 인터럽트 상태만 복원하고 루프를 빠져나간다. .*/
                Thread.currentThread().interrupt();
                log.warn("발행 중 인터럽트 발생. 처리 중단 reviewId={}", event.getReviewId());
                break;
            } catch (Exception e) {
                // 그 외 타임 아웃이나, ExecutionException 이 발생되면, 발행 실패로 끝낸다.
                event.markFailed(e.getMessage());
                log.error("리뷰 이벤트 발행 실패. reviewId={}, reason={}",
                        event.getReviewId(), e.getMessage());
            }
        }
    }

}