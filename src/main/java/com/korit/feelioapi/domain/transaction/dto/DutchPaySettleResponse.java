package com.korit.feelioapi.domain.transaction.dto;

public record DutchPaySettleResponse(
        boolean settled,
        Long newIncomeTransactionId
) {}
