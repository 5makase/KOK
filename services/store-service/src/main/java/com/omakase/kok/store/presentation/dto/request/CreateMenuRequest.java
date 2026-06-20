package com.omakase.kok.store.presentation.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class CreateMenuRequest {

    @NotBlank
    @Size(max = 100)
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
