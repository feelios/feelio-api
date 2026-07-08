package com.korit.feelioapi.domain.transaction.dto;

import java.util.List;

public record TransactionListResponse(
        List<TransactionDto> transactions,
        Long totalIncome,
        Long totalExpense
) {}
