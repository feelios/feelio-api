package com.korit.feelioapi.domain.transaction.dto;

/**
 * DELETE /api/transactions/{transactionId} 응답 data (API-CONTRACT §6).
 * { "deleted": true }
 */
public record TransactionDeleteResponse(
        boolean deleted
) {
}
