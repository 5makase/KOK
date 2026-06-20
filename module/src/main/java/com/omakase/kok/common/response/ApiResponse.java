package com.omakase.kok.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        int status,
        String message,
        T data,
        Object errors
) {

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .status(200)
                .message("SUCCESS")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
                .status(201)
                .message("CREATED")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> updated(T data) {
        return ApiResponse.<T>builder()
                .status(200)
                .message("UPDATED")
                .data(data)
                .build();
    }

    public static ApiResponse<Void> deleted() {
        return ApiResponse.<Void>builder()
                .status(200)
                .message("DELETED")
                .build();
    }

    public static ApiResponse<Void> fail(int status, String message) {
        return ApiResponse.<Void>builder()
                .status(status)
                .message(message)
                .build();
    }

    public static ApiResponse<Void> fail(int status, String message, Object errors) {
        return ApiResponse.<Void>builder()
                .status(status)
                .message(message)
                .errors(errors)
                .build();
    }
}