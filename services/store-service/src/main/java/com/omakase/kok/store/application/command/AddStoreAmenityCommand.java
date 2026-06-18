package com.omakase.kok.store.application.command;

import com.omakase.kok.store.domain.enums.AmenityType;
import com.omakase.kok.store.presentation.dto.request.AddStoreAmenityRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AddStoreAmenityCommand {

    private UUID storeId;
    private UUID requesterId;
    private AmenityType amenityType;

    public static AddStoreAmenityCommand of(UUID storeId, UUID requesterId, AddStoreAmenityRequest request) {
        return AddStoreAmenityCommand.builder()
                .storeId(storeId)
                .requesterId(requesterId)
                .amenityType(request.getAmenityType())
                .build();
    }
}
