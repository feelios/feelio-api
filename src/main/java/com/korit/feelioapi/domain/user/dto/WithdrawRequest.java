package com.korit.feelioapi.domain.user.dto;

/**
 * DELETE /api/users/me 요청 (API-CONTRACT §4). reason 은 선택.
 * (users 에 사유 저장 컬럼이 없어 팀 확정에 따라 받되 저장하지 않는다.)
 */
public record WithdrawRequest(
        String reason
) {
}
