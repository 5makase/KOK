package com.omakase.kok.store.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class CreateStoreRequest {

    @NotNull
    private UUID categoryId;

    @NotBlank
    private String name;

    private String phone;

    @NotBlank
    private String addressSido;

    @NotBlank
    private String addressSigungu;

    private String addressDong;

    private String addressDetail;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private String description;

    private Integer maxCapacity;
}
