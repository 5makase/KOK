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

    @NotBlank
    private String phone;

    @NotBlank
    private String addressSido;

    @NotBlank
    private String addressSigungu;

    private String addressDong;

    private String addressDetail;

    @NotNull
    private BigDecimal latitude;

    @NotNull
    private BigDecimal longitude;

    private String description;

    @NotNull
    private Integer maxCapacity;
}
