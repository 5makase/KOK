package com.omakase.kok.store.domain.repository;

import com.omakase.kok.store.domain.enums.AmenityType;
import com.omakase.kok.store.domain.enums.StoreStatus;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 매장 검색 조건 객체
 * StoreRepository 인터페이스의 search() 파라미터로 사용되며, application 계층에서 생성해 domain 계층에 전달한다.
 */
@Getter
@Builder(toBuilder = true)
public class StoreSearchCondition {

    // 대분류 ID가 주어지면 StoreService.resolveCategoryIds()가 소분류 ID 목록으로 확장해 주입한다
    private List<UUID> categoryIds;
    private String sido;
    private String sigungu;
    private String keyword;
    private List<AmenityType> amenities;
    private UUID ownerId;
    private StoreStatus status;
    private SortType sort;

    // 위치 기반 검색 (고도화) - latitude/longitude/radiusKm 모두 있을 때만 활성화
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Double radiusKm;

    public enum SortType {
        CREATED_AT, RATING, REVIEW_COUNT, DISTANCE
    }

}
