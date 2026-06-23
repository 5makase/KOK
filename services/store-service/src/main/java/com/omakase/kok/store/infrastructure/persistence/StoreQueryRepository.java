package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.QStore;
import com.omakase.kok.store.domain.entity.QStoreAmenity;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.enums.AmenityType;
import com.omakase.kok.store.domain.enums.StoreStatus;
import com.omakase.kok.store.domain.repository.StoreSearchCondition;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberTemplate;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class StoreQueryRepository {

    private final JPAQueryFactory queryFactory;

    public Page<Store> search(StoreSearchCondition condition, Pageable pageable) {
        QStore store = QStore.store;
        QStoreAmenity amenity = QStoreAmenity.storeAmenity;

        boolean hasAmenityFilter = condition.getAmenities() != null && !condition.getAmenities().isEmpty();

        // WHERE 조건을 한 곳에서 관리 - content/count 쿼리 간 불일치 방지
        BooleanExpression[] where = buildWhere(store, amenity, condition);

        // 편의시설 필터 사용 시에만 JOIN - 중복 로우 방지를 위해 selectDistinct
        JPAQuery<Store> contentQuery = queryFactory
                .selectDistinct(store)
                .from(store);
        if (hasAmenityFilter) contentQuery.leftJoin(store.amenities, amenity);

        List<Store> content = contentQuery
                .where(where)
                .orderBy(resolveSort(store, condition))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 마지막 페이지이거나 content 수가 pageSize 미만이면 count 쿼리 생략
        JPAQuery<Long> countQuery = queryFactory
                .select(store.countDistinct())
                .from(store);
        if (hasAmenityFilter) countQuery.leftJoin(store.amenities, amenity);

        countQuery.where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression[] buildWhere(QStore store, QStoreAmenity amenity,
                                            StoreSearchCondition condition) {
        return new BooleanExpression[]{
                store.deletedAt.isNull(),
                eqCategory(store, condition.getCategoryId()),
                eqSido(store, condition.getSido()),
                eqSigungu(store, condition.getSigungu()),
                containsKeyword(store, condition.getKeyword()),
                eqStatus(store, condition.getStatus()),
                eqOwner(store, condition.getOwnerId()),
                inAmenities(amenity, condition.getAmenities()),
                withinRadius(store, condition.getLatitude(), condition.getLongitude(), condition.getRadiusKm())
        };
    }

    private BooleanExpression eqCategory(QStore store, UUID categoryId) {
        return categoryId != null ? store.category.categoryId.eq(categoryId) : null;
    }

    private BooleanExpression eqSido(QStore store, String sido) {
        return sido != null ? store.address.sido.eq(sido) : null;
    }

    private BooleanExpression eqSigungu(QStore store, String sigungu) {
        return sigungu != null ? store.address.sigungu.eq(sigungu) : null;
    }

    private BooleanExpression containsKeyword(QStore store, String keyword) {
        return keyword != null ? store.name.containsIgnoreCase(keyword) : null;
    }

    private BooleanExpression eqStatus(QStore store, StoreStatus status) {
        // OPEN 강제 여부는 Service.resolveCondition()에서 결정
        // null이면 전체 조회
        return status != null ? store.status.eq(status) : null;
    }

    private BooleanExpression eqOwner(QStore store, UUID ownerId) {
        return ownerId != null ? store.ownerId.eq(ownerId) : null;
    }

    private BooleanExpression inAmenities(QStoreAmenity amenity, List<AmenityType> amenities) {
        // 다중 편의시설 조건은 IN 절로 처리 - 하나라도 포함하면 노출 (soft delete 제외)
        if (amenities == null || amenities.isEmpty()) return null;
        return amenity.amenityType.in(amenities).and(amenity.deletedAt.isNull());
    }

    // latitude/longitude/radiusKm 셋 모두 있을 때만 반경 필터 활성화
    private BooleanExpression withinRadius(QStore store, BigDecimal lat, BigDecimal lng, Double radiusKm) {
        if (lat == null || lng == null || radiusKm == null) return null;
        return distanceExpression(store, lat, lng).loe(radiusKm);
    }

    private OrderSpecifier<?> resolveSort(QStore store, StoreSearchCondition condition) {
        if (condition.getSort() == null) return store.createdAt.desc();
        return switch (condition.getSort()) {
            case RATING -> store.averageRating.desc();
            case REVIEW_COUNT -> store.reviewCount.desc();
            // DISTANCE 정렬은 반경 조건(withinRadius)과 함께 사용 — 좌표 없으면 createdAt으로 fallback
            case DISTANCE -> condition.getLatitude() != null && condition.getLongitude() != null
                    ? distanceExpression(store, condition.getLatitude(), condition.getLongitude()).asc()
                    : store.createdAt.desc();
            default -> store.createdAt.desc();
        };
    }

    // DISTANCE 정렬용 Haversine 수식 - 기준 좌표(lat, lng) 대비 각 매장까지의 거리
    private NumberTemplate<Double> distanceExpression(QStore store, BigDecimal lat, BigDecimal lng) {
        return Expressions.numberTemplate(Double.class,
                "6371 * acos(cos(radians({0})) * cos(radians({1})) * cos(radians({2}) - radians({3})) + sin(radians({0})) * sin(radians({1})))",
                lat, store.address.latitude, store.address.longitude, lng
        );
    }
}
