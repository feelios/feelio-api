package com.korit.feelioapi.global.exception;

import lombok.Getter;

/**
 * 도메인 서비스에서 계약상 에러 코드로 흐름을 중단시킬 때 사용하는 런타임 예외.
 * 예) throw new BusinessException(ErrorCode.NOT_FOUND);
 * GlobalExceptionHandler 가 잡아 실패 봉투 + 매핑된 HTTP 상태로 변환한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
