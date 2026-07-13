package com.korit.feelioapi.domain.analysis.dto;

import java.util.List;

public record BudgetStatusResponse(
        List<BudgetItem> budgetItems
) {
    public record BudgetItem(
            String name,
            String emotion,
            Long currentAmount,
            Long prevAmount,
            Long budget
    ) {}
}
