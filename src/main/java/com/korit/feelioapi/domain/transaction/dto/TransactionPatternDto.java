package com.korit.feelioapi.domain.transaction.dto;

public record TransactionPatternDto(
        String timeSlot,
        Long emotionId,
        String merchantName,
        int count,
        int totalAmount
) {}
