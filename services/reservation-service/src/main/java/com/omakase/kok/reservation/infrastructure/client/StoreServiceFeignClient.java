package com.omakase.kok.reservation.infrastructure.client;

import com.omakase.kok.common.dto.ApiResponse;
import com.omakase.kok.reservation.infrastructure.client.dto.BusinessHoursValidationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@FeignClient(name = "store-service", path = "/api/v1/internal/stores")
public interface StoreServiceFeignClient {

    @GetMapping("/{storeId}/hours/validate")
    ApiResponse<BusinessHoursValidationResponse> validateBusinessHours(
            @PathVariable UUID storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time
    );
}
