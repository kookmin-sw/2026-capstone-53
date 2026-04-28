package com.todayway.backend.common.response;

public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(String code, String message, Object details) {}

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new ErrorBody(code, message, null));
    }

    public static ErrorResponse of(String code, String message, Object details) {
        return new ErrorResponse(new ErrorBody(code, message, details));
    }
}
