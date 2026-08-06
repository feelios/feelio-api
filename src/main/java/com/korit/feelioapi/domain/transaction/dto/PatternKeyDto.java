package com.korit.feelioapi.domain.transaction.dto;

public record PatternKeyDto(
        String timeSlot,
        Long emotionId,
        Long categoryId,
        int count,
        long totalAmount
) {}
