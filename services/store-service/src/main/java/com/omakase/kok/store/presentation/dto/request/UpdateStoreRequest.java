package com.omakase.kok.store.presentation.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class UpdateStoreRequest {

    private UUID categoryId;

    private String name;

    private String phone;

    private String addressSido;

    private String addressSigungu;

    private String addressDong;

    private String addressDetail;

    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private BigDecimal latitude;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private BigDecimal longitude;

    private String description;

    // null이면 변경 안 함, 값이 있으면 1 이상이어야 함
    @Positive
    private Integer maxCapacity;
}
