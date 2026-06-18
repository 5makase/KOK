package com.omakase.kok.store.infrastructure.persistence;

import com.omakase.kok.store.domain.entity.QStore;
import com.omakase.kok.store.domain.entity.QStoreAmenity;
import com.omakase.kok.store.domain.entity.Store;
import com.omakase.kok.store.domain.enums.AmenityType;
import com.omakase.kok.store.domain.enums.StoreStatus;
import com.omakase.kok.store.domain.repository.StoreSearchCondition;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class StoreQueryRepository {

    private final JPAQueryFactory queryFactory;

    public Page<Store> search(StoreSearchCondition condition, Pageable pageable) {
        QStore store = QStore.store;
        QStoreAmenity amenity = QStoreAmenity.storeAmenity;

        // 편의시설 필터 사용 시 중복 로우 방지를 위해 selectDistinct
        List<Store> content = queryFactory
                .selectDistinct(store)
                .from(store)
                .leftJoin(store.amenities, amenity)
                .where(
                        store.deletedAt.isNull(),
                        eqCategory(store, condition.getCategoryId()),
                        eqSido(store, condition.getSido()),
                        eqSigungu(store, condition.getSigungu()),
                        containsKeyword(store, condition.getKeyword()),
                        eqStatus(store, condition.getStatus()),
                        eqOwner(store, condition.getOwnerId()),
                        inAmenities(amenity, condition.getAmenities())
                )
                .orderBy(resolveSort(store, condition.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // count 쿼리는 content 쿼리와 동일한 조건으로 별도 실행 (PageImpl 생성용)
        Long total = queryFactory
                .select(store.countDistinct())
                .from(store)
                .leftJoin(store.amenities, amenity)
                .where(
                        store.deletedAt.isNull(),
                        eqCategory(store, condition.getCategoryId()),
                        eqSido(store, condition.getSido()),
                        eqSigungu(store, condition.getSigungu()),
                        containsKeyword(store, condition.getKeyword()),
                        eqStatus(store, condition.getStatus()),
                        eqOwner(store, condition.getOwnerId()),
                        inAmenities(amenity, condition.getAmenities())
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }

    private BooleanExpression eqCategory(QStore store, UUID categoryId) {
        return categoryId != null ? store.category.categoryId.eq(categoryId) : null;
    }

    private BooleanExpression eqSido(QStore store, String sido) {
        return sido != null ? store.addressSido.eq(sido) : null;
    }

    private BooleanExpression eqSigungu(QStore store, String sigungu) {
        return sigungu != null ? store.addressSigungu.eq(sigungu) : null;
    }

    private BooleanExpression containsKeyword(QStore store, String keyword) {
        return keyword != null ? store.name.containsIgnoreCase(keyword) : null;
    }

    private BooleanExpression eqStatus(QStore store, StoreStatus status) {
        // status 미지정 시 OPEN 매장만 기본 노출
        return status != null ? store.status.eq(status) : store.status.eq(StoreStatus.OPEN);
    }

    private BooleanExpression eqOwner(QStore store, UUID ownerId) {
        // ownerId 지정 시 내 매장 목록 조회로 동작
        return ownerId != null ? store.ownerId.eq(ownerId) : null;
    }

    private BooleanExpression inAmenities(QStoreAmenity amenity, List<AmenityType> amenities) {
        // 다중 편의시설 조건은 IN 절로 처리 — 하나라도 포함하면 노출
        return (amenities != null && !amenities.isEmpty()) ? amenity.amenityType.in(amenities) : null;
    }

    private OrderSpecifier<?> resolveSort(QStore store, StoreSearchCondition.SortType sort) {
        if (sort == null) return store.createdAt.desc();
        return switch (sort) {
            case RATING -> store.averageRating.desc();
            case REVIEW_COUNT -> store.reviewCount.desc();
            default -> store.createdAt.desc();
        };
    }
}
