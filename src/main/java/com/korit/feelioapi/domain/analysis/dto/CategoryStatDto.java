package com.korit.feelioapi.domain.analysis.dto;

/**
 * 카테고리별 지출 집계 (API-CONTRACT §9 analysis.byCategory).
 */
public record CategoryStatDto(
        Long categoryId,
        String name,
        String type,
        Long amount,
        Long count
) {
}
