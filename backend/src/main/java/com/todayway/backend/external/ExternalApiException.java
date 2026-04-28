package com.todayway.backend.external;

import lombok.Getter;

@Getter
public class ExternalApiException extends RuntimeException {

    public enum Type {
        TIMEOUT,
        API_FAILED,
        NETWORK
    }

    private final String source;
    private final Type type;
    private final Integer httpStatus;

    public ExternalApiException(String source, Type type, Integer httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.source = source;
        this.type = type;
        this.httpStatus = httpStatus;
    }
}
