package com.todayway.backend.external;

import lombok.Getter;

@Getter
public class ExternalApiException extends RuntimeException {

    /**
     * 외부 API 호출 실패의 분류.
     * <p>PushScheduler 등에서 retry 정책 분기에 사용:
     * <ul>
     *   <li>{@link #TIMEOUT}: 응답/연결 타임아웃. 일시 장애 가능, 재시도 가치 있음.</li>
     *   <li>{@link #CLIENT_ERROR}: HTTP 4xx. 요청 자체 문제(잘못된 좌표, 키 만료 등). 재시도 무의미.</li>
     *   <li>{@link #SERVER_ERROR}: HTTP 5xx 또는 비정상 응답(빈 body 등). 재시도 가치 있음.</li>
     *   <li>{@link #NETWORK}: 연결 실패(DNS, connection refused). 재시도 가치 있음.</li>
     * </ul>
     */
    public enum Type {
        TIMEOUT,
        CLIENT_ERROR,
        SERVER_ERROR,
        NETWORK
    }

    /** 외부 API 출처 (exhaustive switch에 사용 가능). */
    public enum Source {
        ODSAY,
        KAKAO_LOCAL
    }

    private final Source source;
    private final Type type;
    private final Integer httpStatus;

    public ExternalApiException(Source source, Type type, Integer httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.source = source;
        this.type = type;
        this.httpStatus = httpStatus;
    }
}
