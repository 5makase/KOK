package com.kok.review.domain.repository;

import com.kok.review.domain.entity.Review;
import com.kok.review.presentation.DTO1.request.ReviewSortType;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import static com.kok.review.domain.entity.QReview.review;
import static com.kok.review.domain.entity.QReviewImage.reviewImage;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class ReviewRepositoryCustomImpl implements ReviewRepositoryCustom {

    //Java 코드로 쿼리를 작성하게 해줄 수 있는 도구
    private final JPAQueryFactory queryFactory;

    /**
     * 나의 리뷰목록 조회
     * @param userId
     * @param sort
     * @param photoOnly
     * @param pageable
     * @return
     */
    @Override
    public Page<Review> searchMyReviews(UUID userId, ReviewSortType sort, boolean photoOnly, Pageable pageable) {
        List<Review> content = queryFactory.
                selectFrom(review)
                .where(
                        review.userId.eq(userId),
                        photoOnlyCondition(photoOnly)
                )
                .orderBy(toOrderSpecifier(sort))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        Long total = queryFactory
                .select(review.count())
                .from(review)
                .where(
                        review.userId.eq(userId),
                        photoOnlyCondition(photoOnly)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }


    /**
     * 필터 적용 + 페이징 처리된 리뷰 목록 조회
     * @param storeId   매장 ID
     * @param sort      정렬 조건
     * @param photoOnly 사진 리뷰만 볼 것인지
     * @param pageable  페이징 기준
     * @return
     */
    @Override
    public Page<Review> searchReviews(UUID storeId, ReviewSortType sort, boolean photoOnly, Pageable pageable) {
        //A 가게에 대한 모든 리뷰 데이터 조회(사진만 있는 리뷰 요청 시, 가능)
        List<Review> content = queryFactory
                .selectFrom(review)
                .where(
                        review.storeId.eq(storeId),
                        photoOnlyCondition(photoOnly)
                )
                .orderBy(toOrderSpecifier(sort))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        //조회된 리뷰의 총 개수 반환
        Long total = queryFactory
                .select(review.count())
                .from(review)
                .where(
                        review.storeId.eq(storeId),
                        photoOnlyCondition(photoOnly)
                )
                .fetchOne();

        //페이징 규칙 적용해서, 반환.
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }



    private BooleanExpression photoOnlyCondition(boolean photoOnly) {
        //false인 경우, null 반환 = 조건 무시됨.
        if (!photoOnly) {
            return null;
        }
        //true인 경우, 이미지가 있는 reviewId를 반환. REVIEW_ID IN(...) 형태로 반환
        return review.reviewId.in(
                queryFactory
                        .select(reviewImage.review.reviewId)// ReviewImage가 가리키는 reviewId를 조회하는데
                        .from(reviewImage) // 리뷰 이미지 테이블에서
                        .distinct()      //중복되는 것은 뺀다.
        );
    }

    private OrderSpecifier<?> toOrderSpecifier(ReviewSortType sort) {
        return switch (sort) {
            case RATING_HIGH -> review.rating.desc();
            case RATING_LOW  -> review.rating.asc();
            case HELPFUL     -> review.likeCount.desc();
            case LATEST      -> review.createdAt.desc();
        };
    }
}
