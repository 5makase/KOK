package com.omakase.kok.store.presentation.dto.request;

import com.omakase.kok.store.domain.enums.AmenityType;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;

import java.util.List;

@Getter
public class AddStoreAmenityRequest {

    @NotEmpty
    private List<AmenityType> amenityTypes;
}
