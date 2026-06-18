package com.omakase.kok.store.presentation.dto.request;

import com.omakase.kok.store.domain.enums.AmenityType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class AddStoreAmenityRequest {

    @NotNull
    private AmenityType amenityType;
}
