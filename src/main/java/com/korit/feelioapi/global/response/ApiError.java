package com.korit.feelioapi.global.response;

import lombok.Getter;

/**
 * 실패 봉투의 error 객체 (API-CONTRACT §1).
 * { "code": "VALIDATION_ERROR", "message": "..." }
 */
@Getter
public class ApiError {

    private final String code;
    private final String message;

    public ApiError(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
