package com.korit.feelioapi.domain.analysis.dto;

public record CategoryPrevStat(
        Long categoryId,
        String categoryName,
        Long prevAmount
) {}
