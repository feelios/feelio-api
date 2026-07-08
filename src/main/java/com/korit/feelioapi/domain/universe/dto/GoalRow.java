package com.korit.feelioapi.domain.universe.dto;

/**
 * goals 조회 결과(내부용) — 소유권 검증 + 목표 요약 구성.
 */
public record GoalRow(
        Long goalId,
        Long userId,
        String name,
        Integer targetAmount,
        Integer currentAmount
) {
}
