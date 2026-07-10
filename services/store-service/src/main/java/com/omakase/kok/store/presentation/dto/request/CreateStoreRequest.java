package com.omakase.kok.store.presentation.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class CreateStoreRequest {

    @NotNull
    private UUID categoryId;

    @NotBlank
    private String name;

    @NotBlank
    private String phone;

    @NotBlank
    private String addressSido;

    @NotBlank
    private String addressSigungu;

    private String addressDong;

    private String addressDetail;

    @NotNull
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private BigDecimal latitude;

    @NotNull
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private BigDecimal longitude;

    private String description;

    @NotNull
    @Positive
    private Integer maxCapacity;
}
