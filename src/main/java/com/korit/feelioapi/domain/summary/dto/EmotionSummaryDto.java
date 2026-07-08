package com.korit.feelioapi.domain.summary.dto;

public record EmotionSummaryDto(
        Long emotionId,
        String name,
        Integer count,
        Long amount
) {}
