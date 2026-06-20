package com.omakase.kok.store.presentation.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Getter;

// PATCH 부분 수정 - 전송된 필드만 반영, null은 기존 값 유지
@Getter
public class UpdateMenuRequest {

    private String name;

    @Min(0)
    private Integer price;

    private String description;

    private String thumbnailUrl;

    @Min(0)
    private Integer displayOrder;
}
