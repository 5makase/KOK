package com.omakase.kok.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final int status;
    private final String message;
    private final T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "SUCCESS", data);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(201, "CREATED", data);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(200, "SUCCESS", null);
    }

    public static ApiResponse<Void> deleted() {
        return new ApiResponse<>(200, "DELETED", null);
    }

    public static ApiResponse<Void> error(int status, String message) {
        return new ApiResponse<>(status, message, null);
    }
}