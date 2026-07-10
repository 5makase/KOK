//package com.kok.review;
//
//import com.kok.review.application.service.ReviewService;
//import com.kok.review.domain.entity.ReviewEligibility;
//import com.kok.review.domain.entity.ReviewOutboxEvent;
//import com.kok.review.domain.repository.ReviewOutboxEventRepository;
//import com.kok.review.domain.repository.ReviewRepository;
//import com.kok.review.infrastructure.messaging.dto.ReservationEvent;
//import com.kok.review.infrastructure.persistence.ReviewEligibilityRepository;
//import com.kok.review.presentation.DTO1.request.ReviewRequestDto;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@SpringBootTest
//@Transactional // 테스트 종료 후 DB를 깔끔하게 롤백하기 위해 추가
//class ReviewOutboxTest {
//
//    @Autowired private ReviewService reviewService;
//    @Autowired private ReviewRepository reviewRepo;
//    @Autowired private ReviewOutboxEventRepository outboxRepo;
//    @Autowired private ReviewEligibilityRepository eligibilityRepo; // 추가됨
//
//    private UUID userId;
//    private UUID reservationId;
//    private UUID storeId;
//
//    @BeforeEach
//    void setUp() {
//        userId = UUID.randomUUID();
//        reservationId = UUID.randomUUID();
//        storeId = UUID.randomUUID();
//
//        // 💡 핵심: 리뷰 작성 전 권한(Eligibility)을 미리 DB에 저장해 둡니다.
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
//    @DisplayName("리뷰 작성 시 Outbox 이벤트가 트랜잭션 내에 함께 저장되어야 한다")
//    void should_save_review_and_event_atomically() {
//        // Given
//        ReviewRequestDto request = makeRequest(reservationId, storeId);
//
//        // When
//        reviewService.createReview(request, userId);
//
//        // Then
//        // 1. 리뷰가 저장되었는지 확인 (isNotEmpty 사용)
//        assertThat(reviewRepo.findByReservationId(reservationId)).isNotEmpty();
//
//        // 2. Outbox 이벤트가 저장되었는지 확인
//        List<ReviewOutboxEvent> events = outboxRepo.findAll();
//        assertThat(events).hasSize(1);
//        assertThat(events.get(0).getPayload()).contains("REVIEW_CREATED");
//    }
//
//    @Test
//    @DisplayName("리뷰 작성 로직 실패 시 어떤 데이터도 저장되지 않아야 한다(트랜잭션 롤백)")
//    void should_rollback_all_data_when_service_fails() {
//        // Given: 유효하지 않은 요청 (강제 예외 발생 유도)
//        ReviewRequestDto invalidRequest = makeInvalidRequest();
//
//        // When
//        try {
//            reviewService.createReview(invalidRequest, userId);
//        } catch (Exception ignored) {
//            // 예외 발생은 의도한 상황임
//        }
//
//        // Then: 어떤 데이터도 저장되지 않아야 함
//        assertThat(reviewRepo.findAll()).isEmpty();
//        assertThat(outboxRepo.findAll()).isEmpty();
//    }
//
//    private ReviewRequestDto makeRequest(UUID reservationId, UUID storeId) {
//        return ReviewRequestDto.builder()
//                .storeId(storeId)
//                .reservationId(reservationId)
//                .rating(new BigDecimal("5.0"))
//                .content("좋은 가게입니다.")
//                .build();
//    }
//
//    private ReviewRequestDto makeInvalidRequest() {
//        return ReviewRequestDto.builder()
//                .storeId(null) // 필수값 누락으로 인한 로직 실패 유도
//                .reservationId(UUID.randomUUID()) // 존재하지 않는 예약 ID
//                .rating(new BigDecimal("99.0"))
//                .build();
//    }
//
//    @Test
//    @DisplayName("중복 리뷰 작성 시도 시 데이터베이스 제약 조건으로 인해 모두 롤백되어야 한다")
//    void should_rollback_when_duplicate_review_is_attempted() {
//        // Given: 첫 번째 성공적인 리뷰 작성
//        reviewService.createReview(makeRequest(reservationId, storeId), userId);
//
//        // When: 동일한 예약 ID로 두 번째 시도
//        try {
//            reviewService.createReview(makeRequest(reservationId, storeId), userId);
//        } catch (Exception ignored) {
//            // 중복 예외 발생 예상
//        }
//
//        // Then: Outbox 테이블에는 처음 작성한 1개의 이벤트만 존재해야 함
//        List<ReviewOutboxEvent> events = outboxRepo.findAll();
//        assertThat(events).hasSize(1); // 2개가 되면 안 됨!
//    }
//
//    @Test
//    @DisplayName("입력 데이터가 유효하지 않으면 리뷰와 이벤트 모두 저장되지 않아야 한다")
//    void should_rollback_when_rating_is_invalid() {
//        // Given: 100점이라는 말도 안 되는 평점 요청
//        ReviewRequestDto invalidRequest = ReviewRequestDto.builder()
//                .storeId(storeId)
//                .reservationId(UUID.randomUUID())
//                .rating(new BigDecimal("100.0")) // 비즈니스 로직 위반
//                .content("잘못된 리뷰")
//                .build();
//
//        // When
//        try {
//            reviewService.createReview(invalidRequest, userId);
//        } catch (Exception ignored) {
//        }
//
//        // Then: 리뷰도, 이벤트도 DB에 0건이어야 함
//        assertThat(reviewRepo.findAll()).isEmpty();
//        assertThat(outboxRepo.findAll()).isEmpty();
//    }
//}