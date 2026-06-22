package com.omakase.kok.store.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

@Getter
public class AddStoreImageRequest {

    @NotEmpty
    @Valid
    private List<@NotNull ImageEntry> images;

    @Getter
    public static class ImageEntry {

        @NotBlank
        private String imageUrl;

        @NotNull
        private Integer displayOrder;
    }
}
