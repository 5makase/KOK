//package com.kok.review;
//
//import com.kok.review.application.service.ReviewOutboxAppender;
//import com.kok.review.application.service.ReviewReplyService;
//import com.kok.review.domain.entity.Review;
//import com.kok.review.domain.repository.ReviewReplyRepository;
//import com.kok.review.domain.repository.ReviewRepository;
//import com.kok.review.global.exception.ReviewErrorCode;
//import com.kok.review.infrastructure.client.StoreClient;
//import com.kok.review.infrastructure.client.dto.StoreResponse;
//import com.kok.review.presentation.DTO1.request.ReviewReplyRequestDto;
//import com.kok.review.presentation.DTO1.request.ReviewRequestDto;
//import com.omakase.kok.common.exception.BaseException;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.test.context.ActiveProfiles;
//
//import java.math.BigDecimal;
//import java.util.UUID;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.atomic.AtomicInteger;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.Mockito.doNothing;
//import static org.mockito.Mockito.when;
//
///**
// * 사장님 답글 중복 생성 동시성 테스트.
// *
// * 시나리오: 같은 리뷰에 사장님이 "따닥"(동시에 여러 번 클릭)으로 답글을 여러 개 만들려 한다.
// * review_id UNIQUE 제약이 물리적 최종 방어선이므로, 정확히 1건만 저장되고
// * 나머지는 DataIntegrityViolationException → REPLY_ALREADY_EXISTS(409)로 튕겨야 한다.
// *
// * 왜 @SpringBootTest 이고 @Transactional 이 없나:
// *   각 스레드가 자기 트랜잭션에서 실제 커밋해야 UNIQUE 충돌이 재현된다.
// *   단일 트랜잭션/롤백이면 flush 시점 충돌이 발생하지 않는다.
// */
//@SpringBootTest
//@ActiveProfiles("test")
//class ReviewReplyServiceConcurrencyTest {
//
//    @Autowired
//    private ReviewReplyService reviewReplyService;
//
//    @Autowired
//    private ReviewRepository reviewRepository;
//
//    @Autowired
//    private ReviewReplyRepository reviewReplyRepository;
//
//    // 외부 Feign 호출은 이 테스트 관심사가 아니므로 목킹
//    @MockBean
//    private StoreClient storeClient;
//
//    // Outbox 저장도 관심사가 아니므로 no-op
//    @MockBean
//    private ReviewOutboxAppender reviewOutboxAppender;
//
//    private UUID reviewId;
//    private UUID ownerId;
//    private ReviewReplyRequestDto requestDto;
//
//    @BeforeEach
//    void setUp() {
//        ownerId = UUID.randomUUID();
//
//        // 답글 대상 리뷰 1건 저장
//        Review review = Review.create(
//                ReviewRequestDto.builder()
//                        .storeId(UUID.randomUUID())
//                        .reservationId(UUID.randomUUID())
//                        .rating(new BigDecimal("4.5"))
//                        .content("테스트용 리뷰 본문입니다. 열 자 이상.")
//                        .build(),
//                UUID.randomUUID());
//        reviewRepository.saveAndFlush(review);
//        this.reviewId = review.getReviewId();
//
//        // 답글 요청 DTO - content를 반드시 채운다 (content 컬럼이 NOT NULL 이므로)
//        // ↓↓↓ 빌더가 없으면 이 부분만 setter 방식으로 교체:
//             this.requestDto = new ReviewReplyRequestDto();
//             this.requestDto.setContent("사장님 답글 테스트 본문입니다.");
//
//        // storeClient.getStore(...)가 "이 사장님(ownerId)이 가게 주인"이라고 응답하도록 스텁
//        // StoreResponse 필드 구성/순서가 다르면 이 생성 부분만 맞춰주세요.
//        StoreResponse store = new StoreResponse(review.getStoreId(), "테스트가게", ownerId);
//        when(storeClient.getStore(any())).thenReturn(store);
//
//        doNothing().when(reviewOutboxAppender)
//                .append(any(), anyString(), any(), any());
//    }
//
//    @AfterEach
//    void tearDown() {
//        reviewReplyRepository.deleteAll();
//        reviewRepository.deleteAll();
//    }
//
//    @Test
//    @DisplayName("같은 리뷰에 답글을 동시에 여러 번 달아도 정확히 1건만 저장된다")
//    void concurrentCreateReply_onlyOneSucceeds() throws InterruptedException {
//        final int threadCount = 16;
//        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
//        CountDownLatch ready = new CountDownLatch(threadCount);
//        CountDownLatch start = new CountDownLatch(1);   // 동시 출발 신호
//        CountDownLatch done = new CountDownLatch(threadCount);
//
//        AtomicInteger success = new AtomicInteger();
//        AtomicInteger alreadyExists = new AtomicInteger();
//        AtomicInteger other = new AtomicInteger();
//
//        for (int i = 0; i < threadCount; i++) {
//            pool.submit(() -> {
//                ready.countDown();
//                try {
//                    start.await();   // 전원 대기하다 동시에 출발
//                    reviewReplyService.createReply(reviewId, ownerId, "OWNER", requestDto);
//                    success.incrementAndGet();
//                } catch (BaseException e) {
//                    // 이미 답글 있음(REPLY_ALREADY_EXISTS)만 정상 실패로 인정
//                    if (e.getErrorCode() == ReviewErrorCode.REPLY_ALREADY_EXISTS) {
//                        alreadyExists.incrementAndGet();
//                    } else {
//                        other.incrementAndGet();
//                    }
//                } catch (Exception e) {
//                    other.incrementAndGet();   // 예상 못한 예외 (있으면 안 됨)
//                } finally {
//                    done.countDown();
//                }
//            });
//        }
//
//        ready.await();          // 전원 준비될 때까지
//        start.countDown();      // 동시 출발
//        done.await();           // 전원 종료 대기
//        pool.shutdown();
//
//        // 정확히 1건 성공, 나머지는 전부 "이미 답글 있음"
//        assertThat(success.get()).isEqualTo(1);
//        assertThat(alreadyExists.get()).isEqualTo(threadCount - 1);
//        assertThat(other.get()).isZero();
//
//        // DB에도 답글은 딱 1건
//        assertThat(reviewReplyRepository.existsByReviewId(reviewId)).isTrue();
//    }
//}