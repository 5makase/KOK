package com.omakase.kok.store.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.UUID;

@Getter
public class UpdateStoreRequest {

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

    private String description;

    private Integer maxCapacity;
}
