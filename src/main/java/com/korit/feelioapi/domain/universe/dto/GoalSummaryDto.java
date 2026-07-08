package com.korit.feelioapi.domain.universe.dto;

/**
 * 시뮬레이션 대상 목표 요약 (API-CONTRACT §9 universe.goal).
 */
public record GoalSummaryDto(
        Long goalId,
        String name,
        Integer targetAmount,
        Integer currentAmount
) {
}
