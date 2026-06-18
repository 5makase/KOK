package com.omakase.kok.store.application.command;

import com.omakase.kok.store.presentation.dto.request.CreateStoreRequest;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class CreateStoreCommand {

    private UUID ownerId;
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

    public static CreateStoreCommand of(UUID ownerId, CreateStoreRequest request) {
        return CreateStoreCommand.builder()
                .ownerId(ownerId)
                .categoryId(request.getCategoryId())
                .name(request.getName())
                .phone(request.getPhone())
                .addressSido(request.getAddressSido())
                .addressSigungu(request.getAddressSigungu())
                .addressDong(request.getAddressDong())
                .addressDetail(request.getAddressDetail())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .description(request.getDescription())
                .maxCapacity(request.getMaxCapacity())
                .build();
    }
}
