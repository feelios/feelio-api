package com.korit.feelioapi.domain.transaction.dto;

import java.time.LocalDateTime;

public record TransactionDto(
        Long transactionId,
        String type,
        Integer amount,
        String memo,
        LocalDateTime occurredAt,
        CategoryDto category,
        EmotionDto emotion,
        Long goalId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
