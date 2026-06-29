//package com.kok.review;
//
//import com.kok.review.application.service.ReviewService;
//import com.kok.review.domain.entity.Review;
//import com.kok.review.domain.entity.ReviewEligibility;
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
//    @Autowired ReviewService reviewService;
//    @Autowired ReviewEligibilityRepository eligibilityRepo;
//    @Autowired ReviewRepository reviewRepository;
//
//    UUID storeId = UUID.randomUUID();
//    UUID userId  = UUID.randomUUID();
//    UUID reservationId = UUID.randomUUID(); // 고정된 예약 ID
//
//    @BeforeEach
//    void setUp() {
//        // 동일한 예약에 대한 권한 1개만 생성
//        ReservationEvent event = new ReservationEvent(
//                UUID.randomUUID(),
//                "RESERVATION_VISITED",
//                reservationId,
//                userId,
//                storeId,
//                LocalDateTime.now()
//        );
//        eligibilityRepo.save(ReviewEligibility.create(event));
//    }
//
//    @Test
//    @DisplayName("동일한 예약으로 중복 리뷰를 작성할 경우 최초 1건만 성공해야 한다")
//    void should_only_allow_one_review_per_reservation_when_requested_concurrently() throws InterruptedException {
//        int N = 5; // 5명이 동시에 같은 예약으로 리뷰 작성 시도
//        ExecutorService executor = Executors.newFixedThreadPool(N);
//        CountDownLatch ready = new CountDownLatch(N);
//        CountDownLatch start = new CountDownLatch(1);
//        CountDownLatch done = new CountDownLatch(N);
//
//        AtomicInteger success = new AtomicInteger(0);
//        AtomicInteger failed = new AtomicInteger(0);
//
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
//        // ── 검증 ──
//        System.out.println("=== 결과 ===");
//        System.out.println("성공: " + success.get() + " / 실패: " + failed.get());
//
//        // 1. 성공은 정확히 1건이어야 함
//        assertThat(success.get()).isEqualTo(1);
//        // 2. 나머지는 모두 실패해야 함
//        assertThat(failed.get()).isEqualTo(N - 1);
//
//        // 3. DB상에서도 리뷰가 실제로 1개만 생성되었는지 최종 확인
//        List<Review> reviews = reviewRepository.findByReservationId(reservationId);
//        assertThat(reviews).hasSize(1);
//    }
//
//    private ReviewRequestDto makeRequest(UUID reservationId) {
//        return ReviewRequestDto.builder()
//                .storeId(storeId)
//                .reservationId(reservationId)
//                .rating(new BigDecimal("5.0"))
//                .content("중복 작성 방지 테스트용 리뷰입니다.")
//                .build();
//    }
//}