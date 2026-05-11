package com.todayway.backend.common.exception;

import com.todayway.backend.common.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code.name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("validation failed");
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code.name(), detail));
    }

    /**
     * Spring 바인딩/검증 단계에서 발생하는 사용자 입력 오류를 일괄 400 VALIDATION_ERROR 매핑 (명세 §1.6).
     * <ul>
     *   <li>{@link HandlerMethodValidationException} — Spring 6.1+ {@code @RequestParam @Min/@Max} 위반.</li>
     *   <li>{@link ConstraintViolationException} — 레거시 controller-level 검증 path.</li>
     *   <li>{@link HttpMessageNotReadableException} — malformed JSON / Content-Type 누락.</li>
     *   <li>{@link MissingServletRequestParameterException} — 필수 query 누락.</li>
     *   <li>{@link MethodArgumentTypeMismatchException} — query 타입 불일치 (예: {@code lat=foo}).</li>
     * </ul>
     *
     * <p>Service-layer 의 {@link IllegalArgumentException} (service 가 직접 throw — 예: ODsay 응답
     * 매핑 실패) 은 본 handler 가 catch 하지 않아 {@link #handleUnknown} 의 500 + ERROR 로깅으로
     * 떨어진다. 그러나 Jackson deserialization 도중 record compact ctor 의 IAE (예: {@code Coordinate}
     * 의 NaN 가드, {@code RouteSegment} invariant) 가 발생하면 Jackson 이 {@link HttpMessageNotReadableException}
     * 으로 wrap 해 본 handler 의 첫 분기에 잡힌다 — 사용자 입력 오류 ({@code 400}) 와 server-side
     * invariant 위반이 같은 path 로 도착하는 셈. root cause 가 IAE 인 후자는 별도 WARN 으로 로깅해
     * 운영 관측성을 보존하면서 응답은 동일하게 400 으로 유지.
     */
    @ExceptionHandler({
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        if (e instanceof HttpMessageNotReadableException hmnr) {
            Throwable root = hmnr.getMostSpecificCause();
            if (root instanceof IllegalArgumentException) {
                // record compact ctor invariant 위반이 deserialization 도중 발생 — 사용자 입력 오류와
                // 구분 안 되는 응답이라도 운영 모니터링용 stack trace 는 남긴다 (server-side bug 시그널).
                log.warn("Deserialization invariant violation — rootCause={}", root.getClass().getSimpleName(), root);
            }
        }
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code.name(), code.getMessage()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code.name(), code.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        ErrorCode code = ErrorCode.UNAUTHORIZED;
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code.name(), code.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        ErrorCode code = ErrorCode.FORBIDDEN_RESOURCE;
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code.name(), code.getMessage()));
    }

    /**
     * 매핑되지 않은 URL 경로 호출 시 404 {@code RESOURCE_NOT_FOUND} 매핑 (명세 §1.6).
     *
     * <p>Spring 6.x default 흐름: {@link NoResourceFoundException}만 throw — static resource handler가
     * 매핑 못 찾은 path 를 잡아 발생. {@link NoHandlerFoundException}은 {@code spring.mvc.throw-exception-if-no-handler-found}
     * 활성화 시에만 발생하며 본 프로젝트 application.yml 에는 미설정이라 production 에서는 발생 X.
     * 미래 설정 활성화 또는 다른 진입점 대비해 둘 다 매핑.
     *
     * <p>로그 레벨 WARN — 4xx 류는 클라이언트 측 오류라 ERROR 부적절 (catch-all 분기가 ERROR 로깅으로
     * 5xx false alarm 유발한 이슈 #33 의 원인 자체). 스택 trace 는 {@code ResourceHttpRequestHandler}
     * 내부 흐름이라 정보 가치 X — {@code resourcePath} 한 줄만 남겨 운영 가시성 (스캐닝/SPA bug/path drift
     * 탐지) 확보.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleNoResourceFound(Exception e) {
        if (e instanceof NoResourceFoundException nrfe) {
            log.warn("NoResourceFound: path={}", nrfe.getResourcePath());
        } else {
            log.warn("NoHandlerFound: {}", e.getMessage());
        }
        ErrorCode code = ErrorCode.RESOURCE_NOT_FOUND;
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code.name(), code.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e) {
        log.error("예상치 못한 예외 발생", e);
        ErrorCode code = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code.name(), code.getMessage()));
    }
}
