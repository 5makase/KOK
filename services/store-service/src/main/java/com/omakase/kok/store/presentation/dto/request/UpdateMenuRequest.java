package com.omakase.kok.store.presentation.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class UpdateMenuRequest {

    @NotBlank
    private String name;

    @NotNull
    @Min(0)
    private Integer price;

    private String description;

    private String thumbnailUrl;

    @NotNull
    @Min(0)
    private Integer displayOrder;
}
