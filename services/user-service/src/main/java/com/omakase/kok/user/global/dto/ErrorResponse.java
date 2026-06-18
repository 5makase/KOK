package com.omakase.kok.user.global.dto;

import lombok.Getter;

@Getter
public class ErrorResponse {

    private final int status;
    private final String message;

    private ErrorResponse(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message);
    }
}