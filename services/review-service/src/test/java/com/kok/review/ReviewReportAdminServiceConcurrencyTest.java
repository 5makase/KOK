//package com.kok.review;
//
//import com.kok.review.application.service.ReviewOutboxAppender;
//import com.kok.review.application.service.ReviewReportAdminService;
//import com.kok.review.domain.entity.ReportReason;
//import com.kok.review.domain.entity.ReportStatus;
//import com.kok.review.domain.entity.Review;
//import com.kok.review.domain.entity.ReviewReport;
//import com.kok.review.domain.repository.ReviewReportRepository;
//import com.kok.review.domain.repository.ReviewRepository;
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
//import java.util.List;
//import java.util.UUID;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.atomic.AtomicInteger;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.Mockito.doNothing;
//
///**
// * 신고 승인/거부 동시성 경합 통합 테스트.
// *
// * 핵심 시나리오: 같은 신고(PENDING)에 대해 여러 요청이 동시에 approve/reject 를 호출한다.
// * 원자적 조건부 UPDATE(transitionIfPending) 덕분에 "정확히 하나"만 성공해야 하고,
// * 나머지는 전부 REPORT_ALREADY_PROCESSED(409)로 튕겨야 한다.
// *
// * 왜 @SpringBootTest 인가:
// *   각 스레드가 자기 트랜잭션에서 실제로 커밋해야 경합이 재현된다.
// *   @DataJpaTest 나 테스트 메서드에 @Transactional 을 붙이면 롤백/단일 트랜잭션이라
// *   동시성 자체가 성립하지 않는다. 그래서 테스트 클래스에는 @Transactional 을 붙이지 않는다.
// *
// * 주의: 원자적 UPDATE 의 격리 동작은 DB 마다 다를 수 있어, 가능하면
// *   Testcontainers(PostgreSQL)로 운영 DB 와 동일 엔진에서 검증하는 것을 권장한다.
// *   (H2 로도 통과하지만, 실제 운영과 같은 보장을 원하면 PostgreSQL 로.)
// */
//@SpringBootTest
//@ActiveProfiles("test")
//class ReviewReportAdminServiceConcurrencyTest {
//
//    @Autowired
//    private ReviewReportAdminService reviewReportAdminService;
//
//    @Autowired
//    private ReviewReportRepository reviewReportRepository;
//
//    @Autowired
//    private ReviewRepository reviewRepository;
//
//    // Outbox 저장은 이 테스트의 관심사가 아니므로 no-op 처리.
//    // (실제 Kafka/직렬화까지 태우지 않고 경합 로직에만 집중)
//    @MockBean
//    private ReviewOutboxAppender reviewOutboxAppender;
//
//    private UUID reportId;
//
//    @BeforeEach
//    void setUp() {
//        doNothing().when(reviewOutboxAppender)
//                .append(org.mockito.ArgumentMatchers.any(),
//                        org.mockito.ArgumentMatchers.anyString(),
//                        org.mockito.ArgumentMatchers.any(),
//                        org.mockito.ArgumentMatchers.any());
//
//        // 신고 대상 리뷰 1건 저장
//        Review review = Review.create(
//                ReviewRequestDto.builder()
//                        .storeId(UUID.randomUUID())
//                        .reservationId(UUID.randomUUID())
//                        .rating(new BigDecimal("4.5"))
//                        .content("테스트용 리뷰 본문입니다. 열 자 이상.")
//                        .build(),
//                UUID.randomUUID());
//        reviewRepository.saveAndFlush(review);
//
//        // PENDING 신고 1건 저장
//        ReviewReport report = ReviewReport.create(
//                review.getReviewId(), UUID.randomUUID(), ReportReason.ABUSE, null);
//        reviewReportRepository.saveAndFlush(report);
//        this.reportId = report.getReportId();
//    }
//
//    @AfterEach
//    void tearDown() {
//        reviewReportRepository.deleteAll();
//        reviewRepository.deleteAll();
//    }
//
//    @Test
//    @DisplayName("동일 신고에 승인 요청이 동시에 여러 건 와도 정확히 1건만 성공한다")
//    void concurrentApprove_onlyOneSucceeds() throws InterruptedException {
//        final int threadCount = 16;
//        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
//        CountDownLatch ready = new CountDownLatch(threadCount);
//        CountDownLatch start = new CountDownLatch(1);   // 동시 출발 신호
//        CountDownLatch done = new CountDownLatch(threadCount);
//
//        AtomicInteger success = new AtomicInteger();
//        AtomicInteger alreadyProcessed = new AtomicInteger();
//        AtomicInteger other = new AtomicInteger();
//
//        for (int i = 0; i < threadCount; i++) {
//            pool.submit(() -> {
//                ready.countDown();
//                try {
//                    start.await();                       // 모든 스레드가 여기서 대기하다 동시에 출발
//                    reviewReportAdminService.approve(reportId, "MASTER");
//                    success.incrementAndGet();
//                } catch (BaseException e) {
//                    // 이미 처리된 경우만 정상적인 실패로 인정
//                    alreadyProcessed.incrementAndGet();
//                } catch (Exception e) {
//                    other.incrementAndGet();             // 예상 못한 예외 (있으면 안 됨)
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
//        // 정확히 하나만 성공, 나머지는 전부 "이미 처리됨"
//        assertThat(success.get()).isEqualTo(1);
//        assertThat(alreadyProcessed.get()).isEqualTo(threadCount - 1);
//        assertThat(other.get()).isZero();
//
//        // 최종 상태는 RESOLVED 로 확정
//        ReviewReport finalReport = reviewReportRepository.findById(reportId).orElseThrow();
//        assertThat(finalReport.getStatus()).isEqualTo(ReportStatus.RESOLVED);
//    }
//
//    @Test
//    @DisplayName("승인과 거부가 동시에 경합해도 최종 상태는 하나로 확정되고 성공은 1건뿐이다")
//    void concurrentApproveAndReject_resolvesToSingleState() throws InterruptedException {
//        final int half = 8;
//        final int threadCount = half * 2;
//        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
//        CountDownLatch start = new CountDownLatch(1);
//        CountDownLatch done = new CountDownLatch(threadCount);
//
//        AtomicInteger success = new AtomicInteger();
//        AtomicInteger alreadyProcessed = new AtomicInteger();
//
//        for (int i = 0; i < threadCount; i++) {
//            final boolean approve = (i % 2 == 0);   // 절반은 승인, 절반은 거부
//            pool.submit(() -> {
//                try {
//                    start.await();
//                    if (approve) {
//                        reviewReportAdminService.approve(reportId, "MASTER");
//                    } else {
//                        reviewReportAdminService.reject(reportId, "MASTER");
//                    }
//                    success.incrementAndGet();
//                } catch (BaseException e) {
//                    alreadyProcessed.incrementAndGet();
//                } catch (Exception ignored) {
//                    // 무시: 아래 assert 에서 success 수로 검증
//                } finally {
//                    done.countDown();
//                }
//            });
//        }
//
//        start.countDown();
//        done.await();
//        pool.shutdown();
//
//        // 승인이든 거부든 통틀어 "처리 성공"은 단 1건이어야 한다.
//        // (동시에 APPROVED + REJECTED 가 둘 다 나가는 정합성 붕괴가 없음을 보장)
//        assertThat(success.get()).isEqualTo(1);
//        assertThat(alreadyProcessed.get()).isEqualTo(threadCount - 1);
//
//        // 최종 상태는 RESOLVED 또는 REJECTED 중 하나로 확정 (PENDING 이 아님)
//        ReviewReport finalReport = reviewReportRepository.findById(reportId).orElseThrow();
//        assertThat(List.of(ReportStatus.RESOLVED, ReportStatus.REJECTED))
//                .contains(finalReport.getStatus());
//    }
//
//    @Test
//    @DisplayName("MASTER 권한이 아니면 전이 시도 전에 막힌다")
//    void nonMasterRole_isRejected() {
//        org.junit.jupiter.api.Assertions.assertThrows(BaseException.class,
//                () -> reviewReportAdminService.approve(reportId, "USER"));
//
//        // 권한에서 막혔으므로 상태는 그대로 PENDING
//        ReviewReport report = reviewReportRepository.findById(reportId).orElseThrow();
//        assertThat(report.getStatus()).isEqualTo(ReportStatus.PENDING);
//    }
//}
