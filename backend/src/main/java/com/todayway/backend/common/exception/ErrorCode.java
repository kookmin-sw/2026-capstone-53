package com.todayway.backend.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청이 유효하지 않습니다"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
    FORBIDDEN_RESOURCE(HttpStatus.FORBIDDEN, "접근 권한이 없습니다"),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다"),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다"),
    ROUTE_NOT_CALCULATED(HttpStatus.NOT_FOUND, "경로가 계산되지 않았습니다"),
    GEOCODE_NO_MATCH(HttpStatus.NOT_FOUND, "지오코딩 결과가 없습니다"),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "푸시 구독을 찾을 수 없습니다"),
    LOGIN_ID_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 로그인 ID입니다"),
    EXTERNAL_ROUTE_API_FAILED(HttpStatus.BAD_GATEWAY, "경로 API 호출에 실패했습니다"),
    MAP_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지도 설정을 가져올 수 없습니다"),
    EXTERNAL_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "외부 API 응답 시간이 초과되었습니다"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다");

    private final HttpStatus status;
    private final String message;
}
