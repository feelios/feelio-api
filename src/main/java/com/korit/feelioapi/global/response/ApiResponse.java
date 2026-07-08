package com.korit.feelioapi.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/**
 * 공통 응답 봉투 (API-CONTRACT §1).
 * 성공: { "success": true, "data": {...} }
 * 실패: { "success": false, "error": { "code": ..., "message": ... } }
 * 성공 시 error, 실패 시 data 는 null 이므로 JSON 직렬화에서 제외한다.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ApiError error;

    private ApiResponse(boolean success, T data, ApiError error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }
}
