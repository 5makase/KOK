//package com.kok.review;
//
//import com.kok.review.domain.entity.ReportReason;
//import com.kok.review.domain.entity.ReportStatus;
//import com.kok.review.domain.entity.ReviewReport;
//import com.kok.review.domain.repository.ReviewReportRepository;
//import com.querydsl.jpa.impl.JPAQueryFactory;
//import jakarta.persistence.EntityManager;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
//import org.springframework.boot.autoconfigure.domain.EntityScan;
//import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
//import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
//import org.springframework.test.context.ActiveProfiles;
//
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@DataJpaTest
//@ActiveProfiles("test")
//@EnableJpaAuditing
//class ReviewReportRepositoryTest {
//
//    @Configuration
//    @EnableAutoConfiguration
//    @EntityScan(basePackages = {"com.kok.review.domain.entity", "com.omakase.kok.common.entity"})
//    @EnableJpaRepositories(basePackages = "com.kok.review.domain.repository")
//    static class TestConfig {
//
//        /**
//         * QueryDSL 커스텀 리포지토리(ReviewRepositoryCustomImpl)가 JPAQueryFactory 를 주입받는다.
//         * 실제 앱에선 별도 설정에서 제공되지만, 이 슬라이스 테스트엔 그 설정이 없으므로 직접 등록한다.
//         */
//        @Bean
//        JPAQueryFactory jpaQueryFactory(EntityManager em) {
//            return new JPAQueryFactory(em);
//        }
//    }
//
//    @Autowired
//    private ReviewReportRepository reviewReportRepository;
//
//    private ReviewReport savePendingReport() {
//        ReviewReport report = ReviewReport.create(
//                UUID.randomUUID(),
//                UUID.randomUUID(),
//                ReportReason.ABUSE,
//                null);
//        return reviewReportRepository.saveAndFlush(report);
//    }
//
//    @Test
//    @DisplayName("PENDING 상태면 RESOLVED 로 전이되고 1행이 변경된다")
//    void transition_fromPending_returnsOne() {
//        ReviewReport report = savePendingReport();
//        int updated = reviewReportRepository.transitionIfPending(
//                report.getReportId(), ReportStatus.RESOLVED);
//        assertThat(updated).isEqualTo(1);
//    }
//
//    @Test
//    @DisplayName("이미 처리된(RESOLVED) 신고는 다시 전이되지 않고 0행이 반환된다")
//    void transition_whenAlreadyResolved_returnsZero() {
//        ReviewReport report = savePendingReport();
//        reviewReportRepository.transitionIfPending(report.getReportId(), ReportStatus.RESOLVED);
//        int updated = reviewReportRepository.transitionIfPending(
//                report.getReportId(), ReportStatus.REJECTED);
//        assertThat(updated).isZero();
//    }
//
//    @Test
//    @DisplayName("존재하지 않는 reportId 는 0행이 반환된다")
//    void transition_whenNotFound_returnsZero() {
//        int updated = reviewReportRepository.transitionIfPending(
//                UUID.randomUUID(), ReportStatus.RESOLVED);
//        assertThat(updated).isZero();
//    }
//}