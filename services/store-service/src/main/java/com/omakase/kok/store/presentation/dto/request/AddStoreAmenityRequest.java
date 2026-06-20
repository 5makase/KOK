package com.omakase.kok.store.presentation.dto.request;

import com.omakase.kok.store.domain.enums.AmenityType;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;

import java.util.List;

// 편의시설 동기화 요청 - 요청 목록이 최종 상태가 됨 (전체 교체)
@Getter
public class AddStoreAmenityRequest {

    @NotEmpty
    private List<AmenityType> amenityTypes;
}
