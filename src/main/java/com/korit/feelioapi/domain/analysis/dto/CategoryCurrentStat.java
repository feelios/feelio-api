package com.korit.feelioapi.domain.analysis.dto;

public record CategoryCurrentStat(
        Long categoryId,
        String categoryName,
        String dominantEmotion,
        Long currentAmount,
        boolean isFixed,
        boolean isBudgetable
) {}
