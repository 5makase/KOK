package com.omakase.kok.store.presentation.dto.response;

import com.omakase.kok.store.application.result.StoreCategoryResult;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// 카테고리 목록 조회용 - 트리 구조 (children 포함)
@Getter
@Builder
public class StoreCategoryResponse {

    private UUID categoryId;
    private String name;
    private int sortOrder;
    private UUID parentId;
    private List<StoreCategoryResponse> children;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StoreCategoryResponse from(StoreCategoryResult result) {
        return StoreCategoryResponse.builder()
                .categoryId(result.getCategoryId())
                .name(result.getName())
                .sortOrder(result.getSortOrder())
                .parentId(result.getParentId())
                .children(result.getChildren().stream()
                        .map(StoreCategoryResponse::from)
                        .toList())
                .createdAt(result.getCreatedAt())
                .updatedAt(result.getUpdatedAt())
                .build();
    }

    // 단건 등록/수정 응답용 - children 없음
    @Getter
    @Builder
    public static class Single {

        private UUID categoryId;
        private String name;
        private int sortOrder;
        private UUID parentId;
        private LocalDateTime updatedAt;

        public static Single from(StoreCategoryResult result) {
            return Single.builder()
                    .categoryId(result.getCategoryId())
                    .name(result.getName())
                    .sortOrder(result.getSortOrder())
                    .parentId(result.getParentId())
                    .updatedAt(result.getUpdatedAt())
                    .build();
        }
    }
}
