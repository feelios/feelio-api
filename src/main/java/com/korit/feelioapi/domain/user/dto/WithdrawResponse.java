package com.korit.feelioapi.domain.user.dto;

/**
 * DELETE /api/users/me 응답 data (API-CONTRACT §4).
 * { "withdrawn": true }
 */
public record WithdrawResponse(
        boolean withdrawn
) {
}
