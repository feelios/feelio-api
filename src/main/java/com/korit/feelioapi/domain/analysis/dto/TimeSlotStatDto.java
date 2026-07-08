package com.korit.feelioapi.domain.analysis.dto;

/**
 * 시간대별 지출 집계 (API-CONTRACT §9 analysis.byTimeSlot).
 * slot: DAWN|MORNING|AFTERNOON|NIGHT / label: 한글 표기.
 */
public record TimeSlotStatDto(
        String slot,
        String label,
        Long amount,
        Long count
) {
}
