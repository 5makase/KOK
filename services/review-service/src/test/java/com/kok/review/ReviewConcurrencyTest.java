//package com.kok.review;
//
//import com.kok.review.application.service.ReviewService;
//import com.kok.review.domain.entity.Review;
//import com.kok.review.domain.entity.ReviewEligibility;
//import com.kok.review.domain.entity.ReviewRatingSummary;
//import com.kok.review.domain.repository.ReviewRatingSummaryRepository;
//import com.kok.review.domain.repository.ReviewRepository;
//import com.kok.review.infrastructure.messaging.dto.ReservationEvent;
//import com.kok.review.infrastructure.persistence.ReviewEligibilityRepository;
//import com.kok.review.presentation.DTO1.request.ReviewRequestDto;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.UUID;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.atomic.AtomicInteger;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@SpringBootTest
//class ReviewConcurrencyTest {
//
//    @Autowired private ReviewService reviewService;
//    @Autowired private ReviewEligibilityRepository eligibilityRepo;
//    @Autowired private ReviewRepository reviewRepository;
//    @Autowired private ReviewRatingSummaryRepository summaryRepo;
//
//    private UUID storeId;
//    private UUID userId;
//
//    @BeforeEach
//    void setUp() {
//        // 공통으로 사용할 매장 및 사용자 ID 초기화
//        storeId = UUID.randomUUID();
//        userId = UUID.randomUUID();
//    }
//
//    @Test
//    @DisplayName("시나리오 1: 여러 사용자가 동시에 같은 매장에 리뷰를 작성할 때 평점이 정확히 누적된다.")
//    void ReviewAccumulationTest() throws InterruptedException {
//        // Given
//        int N = 10; // 10개의 리뷰 동시 작성 시도
//        List<UUID> reservationIds = new ArrayList<>();
//
//        // N개의 개별 예약 ID를 생성하고, 각각에 대한 리뷰 작성 권한(Eligibility)을 부여합니다.
//        for (int i = 0; i < N; i++) {
//            UUID resId = UUID.randomUUID();
//            reservationIds.add(resId);
//
//
//            ReservationEvent.Payload payload = new ReservationEvent.Payload(
//                    resId, userId, storeId, "테스트 오마카세", LocalDateTime.now()
//            );
//            ReservationEvent event = new ReservationEvent(
//                    UUID.randomUUID(), "RESERVATION_VISITED", payload
//            );
//
//            eligibilityRepo.save(ReviewEligibility.create(event));
//        }
//
//        ExecutorService executor = Executors.newFixedThreadPool(N);
//        CountDownLatch ready = new CountDownLatch(N);
//        CountDownLatch start = new CountDownLatch(1);
//        CountDownLatch done  = new CountDownLatch(N);
//
//        AtomicInteger success = new AtomicInteger(0);
//        AtomicInteger failed  = new AtomicInteger(0);
//
//        // When
//        for (int i = 0; i < N; i++) {
//            final UUID reservationId = reservationIds.get(i);
//            executor.submit(() -> {
//                ready.countDown();
//                try {
//                    start.await();
//                    // 개별 예약 ID로 리뷰 작성 요청
//                    reviewService.createReview(makeRequest(reservationId), userId);
//                    success.incrementAndGet();
//                } catch (Exception e) {
//                    failed.incrementAndGet();
//                    System.out.println("요청 실패: " + e.getClass().getSimpleName() + " - " + e.getMessage());
//                } finally {
//                    done.countDown();
//                }
//            });
//        }
//
//        ready.await();
//        start.countDown();    // N개 스레드 동시 출발!
//        done.await();
//        executor.shutdown();
//
//        // Then
//        System.out.println("=== 시나리오 1 테스트 결과 ===");
//        System.out.println("성공 카운트: " + success.get() + " / 실패 카운트: " + failed.get());
//
//        ReviewRatingSummary summary = summaryRepo.findById(storeId).orElseThrow();
//        System.out.println("최종 집계 리뷰 수: " + summary.getReviewCount());
//        System.out.println("최종 집계 합계: " + summary.getRatingSum());
//
//        // 10개가 모두 성공해야 하며 평점 총합은 500(5.0점 * 10명 * 10)이어야 함
//        assertThat(success.get()).isEqualTo(N);
//        assertThat(summary.getReviewCount()).isEqualTo(N);
//        assertThat(summary.getRatingSum()).isEqualTo(50L * N);
//    }
//
//    @Test
//    @DisplayName("시나리오 2: 동일한 예약으로 중복 리뷰를 작성할 경우 최초 1건만 성공해야 한다.")
//    void should_only_allow_one_review_per_reservation_when_requested_concurrently() throws InterruptedException {
//        // Given
//        UUID reservationId = UUID.randomUUID();
//
//        // 💡 변경점: 중첩된 Payload 구조에 맞게 이벤트 생성 (단 1개의 예약 권한만 생성)
//        ReservationEvent.Payload payload = new ReservationEvent.Payload(
//                reservationId, userId, storeId, "테스트 오마카세", LocalDateTime.now()
//        );
//        ReservationEvent event = new ReservationEvent(
//                UUID.randomUUID(), "RESERVATION_VISITED", payload
//        );
//
//        eligibilityRepo.save(ReviewEligibility.create(event));
//
//        int N = 5; // 5번 동시에 동일 예약 건으로 리뷰 작성 시도
//        ExecutorService executor = Executors.newFixedThreadPool(N);
//        CountDownLatch ready = new CountDownLatch(N);
//        CountDownLatch start = new CountDownLatch(1);
//        CountDownLatch done = new CountDownLatch(N);
//
//        AtomicInteger success = new AtomicInteger(0);
//        AtomicInteger failed = new AtomicInteger(0);
//
//        // When
//        for (int i = 0; i < N; i++) {
//            executor.submit(() -> {
//                ready.countDown();
//                try {
//                    start.await();
//                    reviewService.createReview(makeRequest(reservationId), userId);
//                    success.incrementAndGet();
//                } catch (Exception e) {
//                    failed.incrementAndGet();
//                } finally {
//                    done.countDown();
//                }
//            });
//        }
//
//        ready.await();
//        start.countDown();
//        done.await();
//        executor.shutdown();
//
//        // Then
//        System.out.println("=== 시나리오 2 테스트 결과 ===");
//        System.out.println("성공: " + success.get() + " / 실패: " + failed.get());
//
//        // 1. 성공은 정확히 1건이어야 함
//        assertThat(success.get()).isEqualTo(1);
//        // 2. 나머지는 모두 실패해야 함
//        assertThat(failed.get()).isEqualTo(N - 1);
//        // 3. DB상에서도 리뷰가 실제로 1개만 생성되었는지 최종 확인
//        List<Review> reviews = reviewRepository.findByReservationId(reservationId);
//        assertThat(reviews).hasSize(1);
//    }
//
//    // 공통 Request 생성 메서드
//    private ReviewRequestDto makeRequest(UUID reservationId) {
//        return ReviewRequestDto.builder()
//                .storeId(storeId)
//                .reservationId(reservationId)
//                .rating(new BigDecimal("5.0"))
//                .content("동시성 테스트용 리뷰입니다.")
//                .build();
//    }
//}