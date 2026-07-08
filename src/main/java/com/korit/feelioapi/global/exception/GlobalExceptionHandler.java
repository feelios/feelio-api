package com.korit.feelioapi.global.exception;

import com.korit.feelioapi.global.response.ApiError;
import com.korit.feelioapi.global.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 핸들러.
 * 모든 예외를 계약 §1 실패 봉투 + 매핑된 HTTP 상태로 변환한다.
 *
 * 참고: UNAUTHORIZED / TOKEN_EXPIRED / FORBIDDEN 은 BusinessException 경로로 열어두었다.
 *       Security 필터 단계의 인증 실패 변환은 security 이슈 범위이며 여기서 다루지 않는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 도메인 계층에서 명시적으로 던진 계약 에러. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return build(e.getErrorCode(), e.getMessage());
    }

    /** @Valid 바디 검증 실패 → VALIDATION_ERROR(400). 첫 필드 에러 메시지를 노출한다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : ErrorCode.VALIDATION_ERROR.getDefaultMessage();
        return build(ErrorCode.VALIDATION_ERROR, message);
    }

    /** @Validated 파라미터 검증 실패 → VALIDATION_ERROR(400). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        return build(ErrorCode.VALIDATION_ERROR, e.getMessage());
    }

    /** 그 외 미처리 예외 → INTERNAL_ERROR(500). 내부 메시지는 노출하지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage());
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode errorCode, String message) {
        ApiError error = new ApiError(errorCode.name(), message);
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.fail(error));
    }
}
