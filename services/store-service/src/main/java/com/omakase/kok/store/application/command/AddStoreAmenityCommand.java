package com.omakase.kok.store.application.command;

import com.omakase.kok.store.domain.enums.AmenityType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

// 편의시설 동기화 커맨드 - 요청 목록으로 전체 교체 (PUT /amenities)
@Getter
@Builder
public class AddStoreAmenityCommand {

    private UUID storeId;
    private UUID requesterId;
    private List<AmenityType> amenityTypes;
}
