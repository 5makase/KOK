package com.omakase.kok.store.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

@Getter
public class CreateStoreHoursBulkRequest {

    @NotEmpty
    @Size(min = 7, max = 7, message = "영업시간은 7일치를 모두 입력해야 합니다.")
    @Valid
    private List<CreateStoreHoursRequest> hours;
}
