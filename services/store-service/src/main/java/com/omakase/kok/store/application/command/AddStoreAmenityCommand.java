package com.omakase.kok.store.application.command;

import com.omakase.kok.store.domain.enums.AmenityType;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AddStoreAmenityCommand {

    private UUID storeId;
    private UUID requesterId;
    private AmenityType amenityType;
}
