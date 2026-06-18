package com.omakase.kok.store.presentation.dto.request;

import com.omakase.kok.store.domain.enums.StoreStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ChangeStoreStatusRequest {

    @NotNull
    private StoreStatus status;
}
