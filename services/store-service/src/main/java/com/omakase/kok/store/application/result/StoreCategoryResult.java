package com.omakase.kok.store.application.result;

import com.omakase.kok.store.domain.entity.StoreCategory;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class StoreCategoryResult {

    private UUID categoryId;
    private String name;
    private int sortOrder;
    private List<StoreCategoryResult> children;

    // 단건 생성/수정 응답용 - children LAZY 로딩 없음
    public static StoreCategoryResult from(StoreCategory category) {
        return StoreCategoryResult.builder()
                .categoryId(category.getCategoryId())
                .name(category.getName())
                .sortOrder(category.getSortOrder())
                .children(List.of())
                .build();
    }

    // 트리 목록 조회용 - JOIN FETCH로 children이 이미 로딩된 경우에만 호출
    public static StoreCategoryResult withChildren(StoreCategory category) {
        return StoreCategoryResult.builder()
                .categoryId(category.getCategoryId())
                .name(category.getName())
                .sortOrder(category.getSortOrder())
                .children(category.getChildren().stream()
                        .map(StoreCategoryResult::from)
                        .toList())
                .build();
    }
}
