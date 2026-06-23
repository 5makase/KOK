package com.omakase.kok.store.presentation.dto.request;

import com.omakase.kok.store.domain.enums.AmenityType;
import com.omakase.kok.store.domain.enums.StoreStatus;
import com.omakase.kok.store.domain.repository.StoreSearchCondition;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
public class StoreSearchRequest {

    private UUID categoryId;
    private String sido;
    private String sigungu;
    private String keyword;
    private List<AmenityType> amenities;
    private StoreStatus status;
    private StoreSearchCondition.SortType sort;

    @DecimalMin("-90.0") @DecimalMax("90.0")
    private BigDecimal latitude;

    @DecimalMin("-180.0") @DecimalMax("180.0")
    private BigDecimal longitude;

    private Double radiusKm;
}
