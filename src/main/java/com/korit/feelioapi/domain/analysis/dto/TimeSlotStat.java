package com.korit.feelioapi.domain.analysis.dto;

/**
 * 시간대 집계 매퍼 결과 행(내부용). label·정렬은 Service 에서 부여한다.
 */
public record TimeSlotStat(
        String slot,
        Long amount,
        Long count
) {
}
