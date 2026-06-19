package com.omakase.kok.store.presentation.dto.request;

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

    private BigDecimal latitude;

    private BigDecimal longitude;

    private String description;

    private Integer maxCapacity;
}
